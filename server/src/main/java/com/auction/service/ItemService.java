package com.auction.service;

import com.auction.dao.ItemDAO;
import com.auction.dao.AuctionDAO;
import com.auction.dao.BidTransactionDAO;
import com.auction.dao.UserDAO;
import com.auction.factory.ItemFactory;
import com.auction.manager.AuctionManager;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.item.Item;
import com.auction.model.user.User;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * ItemService - Xử lý logic nghiệp vụ liên quan đến sản phẩm đấu giá.
 */
public class ItemService {

    private static final Logger LOGGER = Logger.getLogger(ItemService.class.getName());
    private final ItemDAO itemDAO;
    private final AuctionDAO auctionDAO;
    private final BidTransactionDAO bidTransactionDAO;
    private final UserDAO userDAO;

    public ItemService() {
        this(new ItemDAO(), new AuctionDAO(), new BidTransactionDAO(), new UserDAO());
    }

    ItemService(ItemDAO itemDAO, AuctionDAO auctionDAO, BidTransactionDAO bidTransactionDAO, UserDAO userDAO) {
        this.itemDAO = itemDAO;
        this.auctionDAO = auctionDAO;
        this.bidTransactionDAO = bidTransactionDAO;
        this.userDAO = userDAO;
    }

    ItemService(ItemDAO itemDAO, AuctionDAO auctionDAO, UserDAO userDAO) {
        this(itemDAO, auctionDAO, new BidTransactionDAO(), userDAO);
    }

    // ── CREATE ────────────────────────────────────────────────────────────

    /**
     * Tạo sản phẩm mới bằng ItemFactory.
     * @param type         loại sản phẩm: ELECTRONICS, ART, VEHICLE
     * @param name         tên sản phẩm
     * @param description  mô tả
     * @param startingPrice giá khởi điểm
     * @param sellerId     ID người bán
     */
    public Item createItem(String type, String name, String description,
                            double startingPrice, String sellerId) {
        // Validate
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Tên mặt hàng không được để trống.");
        if (startingPrice <= 0)
            throw new IllegalArgumentException("Giá khởi điểm phải dương.");

        String id = UUID.randomUUID().toString();
        Item item = ItemFactory.createItem(id, type, name, description, startingPrice);

        boolean saved = itemDAO.insert(item, sellerId);
        if (!saved) throw new RuntimeException("Không thể lưu mục vào cơ sở dữ liệu.");

        LOGGER.info("Item created: " + name + " [" + type + "] by seller=" + sellerId);
        return item;
    }

    // ── READ ──────────────────────────────────────────────────────────────

    public Item getItemById(String itemId) {
        Item item = itemDAO.findById(itemId);
        if (item == null) throw new IllegalArgumentException("Không tìm thấy mục: " + itemId);
        return item;
    }

    public List<Item> getAllItems() {
        return itemDAO.findAll();
    }

    public List<Item> getItemsBySeller(String sellerId) {
        return itemDAO.findBySeller(sellerId);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────

    /**
     * Cập nhật thông tin sản phẩm.
     * Chỉ seller sở hữu hoặc ADMIN mới được sửa.
     */
    public void updateItem(String itemId, String type, String name, String description,
                           double startingPrice, String requesterId) {
        Item existing = itemDAO.findById(itemId);
        if (existing == null) throw new IllegalArgumentException("Không tìm thấy mục: " + itemId);
        ensureCanManageItem(existing, requesterId);

        if (startingPrice <= 0) {
            throw new IllegalArgumentException("Giá khởi điểm phải dương.");
        }

        String newType = (type != null && !type.isBlank()) ? type : existing.getType();
        String newName = (name != null && !name.isBlank()) ? name : existing.getName();
        String newDesc = (description != null) ? description : existing.getDescription();

        boolean updated = itemDAO.update(itemId, newType, newName, newDesc, startingPrice);
        if (!updated) throw new RuntimeException("Không thể cập nhật mục: " + itemId);

        LOGGER.info("Item updated: " + itemId + " by " + requesterId);
    }

    public void updateItem(String itemId, String name, String description, String requesterId) {
        Item existing = itemDAO.findById(itemId);
        if (existing == null) throw new IllegalArgumentException("Không tìm thấy mục: " + itemId);
        ensureCanManageItem(existing, requesterId);

        String newName = (name != null && !name.isBlank()) ? name : existing.getName();
        String newDesc = (description != null) ? description : existing.getDescription();

        boolean updated = itemDAO.update(itemId, newName, newDesc);
        if (!updated) throw new RuntimeException("Không thể cập nhật mục: " + itemId);

        LOGGER.info("Item updated: " + itemId + " by " + requesterId);
    }

    // ── DELETE ────────────────────────────────────────────────────────────

    public void deleteItem(String itemId, String requesterId) {
        Item existing = itemDAO.findById(itemId);
        if (existing == null) throw new IllegalArgumentException("Không tìm thấy mục: " + itemId);
        ensureCanManageItem(existing, requesterId);

        List<Auction> auctions = auctionDAO.findByItemId(itemId);
        if (auctions.isEmpty() && auctionDAO.existsByItemId(itemId)) {
            throw new IllegalStateException("Không thể xóa sản phẩm đã được dùng trong phiên đấu giá");
        }
        for (Auction auction : auctions) {
            boolean hasBids = bidTransactionDAO.hasBids(auction.getId());
            if (!canDeleteAuctionForItem(auction, hasBids)) {
                throw new IllegalStateException(
                        "Không thể xóa sản phẩm vì phiên " + auction.getId() + ": "
                                + deleteAuctionBlockedMessage(auction));
            }
        }

        for (Auction auction : auctions) {
            auctionDAO.delete(auction.getId());
            AuctionManager.getInstance().removeAuction(auction.getId());
        }

        boolean deleted = itemDAO.delete(itemId);
        if (!deleted) throw new RuntimeException("Không thể xóa mục: " + itemId);

        LOGGER.info("Item deleted: " + itemId + " by " + requesterId);
    }

    private void ensureCanManageItem(Item item, String requesterId) {
        User requester = userDAO.findById(requesterId);
        if (requester != null && "ADMIN".equals(requester.getRole())) return;

        if (item.getSellerId() == null || !item.getSellerId().equals(requesterId)) {
            throw new IllegalStateException("Không thể chỉnh sửa vật phẩm của người khác");
        }
    }

    private boolean canDeleteAuctionForItem(Auction auction, boolean hasBids) {
        if (auction.getStatus() == AuctionStatus.CANCELED || auction.getStatus() == AuctionStatus.PAID) {
            return true;
        }
        return !hasBids;
    }

    private String deleteAuctionBlockedMessage(Auction auction) {
        if (auction.getStatus() == AuctionStatus.FINISHED && auction.getCurrentWinnerId() != null) {
            return "phiên đã kết thúc và có người thắng, vui lòng đợi người thắng thanh toán trước khi xóa";
        }
        return "chỉ xóa được phiên đã CANCELED/PAID hoặc phiên chưa có bid";
    }
}
