**Mối quan hệ**

## Mạnh (Server core) → là nền tảng của tất cả

Nghĩa phụ thuộc vào Mạnh vì dao/service/ cần dùng các class từ model/ của Mạnh (ví dụ: User, Item, Auction...)
Minh phụ thuộc vào Mạnh vì client giao tiếp qua network/ của Mạnh
Tuân phụ thuộc vào Mạnh vì AutoBidder, AntiSniping cần gọi vào AuctionManager của Mạnh

→ Mạnh phải làm trước, vì Nghĩa, Minh, Tuân đều cần Mạnh xong mới làm được

## Nghĩa (Data layer) → phục vụ cho Mạnh và Minh

Nghĩa viết service/ để Mạnh có thể gọi logic nghiệp vụ
Minh gián tiếp dùng kết quả từ Nghĩa thông qua server (dữ liệu hiển thị lên giao diện đều từ DB của Nghĩa)

→ Nghĩa làm song song với Mạnh, nhưng cần Mạnh định nghĩa model trước

## Minh (Client) → phụ thuộc vào Mạnh và Nghĩa

Minh chỉ bắt đầu làm hiệu quả khi Mạnh đã có network/ và Nghĩa đã có service/ chạy được
Minh không ảnh hưởng ngược lại Mạnh hay Nghĩa

→ Minh nên làm sau, hoặc làm giao diện trước rồi kết nối sau

## Tuân (Advanced + Test) → làm cuối cùng

Test cần Mạnh, Nghĩa, Minh hoàn thiện mới test được
AutoBidder, AntiSniping cần server của Mạnh chạy ổn định trước

→ Tuân làm cuối, nhưng có thể setup CI/CD và viết test case sớm

---

## Thành viên nhóm

| Họ tên | Phụ trách |
|--------|-----------|
| Mạnh | Server core: `model/`, `Main.java`, `network/`, `manager/`,... |
| Nghĩa | Server data: `dao/`, `service/`, `factory/`, `exception/`, `database/`,... |
| Minh | Client: `controller/`, `network/`, `chart/`, toàn bộ `.fxml`,... |
| Tuân | Advanced: `AutoBidder.java`, `AntiSnipingTimer.java`, `BidHistoryVisualizer.java`, `test/`, `pom.xml`, CI/CD,... |

---

## Kiến trúc hệ thống

```
CLIENT (JavaFX + MVC)  ◄──── JSON/Socket Port 9999 ────►  SERVER (Java)
                                                                  │
                                                           MySQL Database
                                                            auction_db
```

## Các tầng phía Server
- **Model** – Entity, User (Bidder/Seller/Admin), Item (Electronics/ArtItem/Vehicle), Auction, BidTransaction
- **DAO** – UserDAO, ItemDAO, AuctionDAO, BidTransactionDAO (JDBMinh + MySQL)
- **Service** – UserService, AuctionService, ItemService
- **Network** – ServerMain (thread pool 50), ClientHandler (xử lý request JSON)
- **Manager** – AuctionManager (Singleton, broadcast realtime)

## Các tầng phía Client
- **Controller** – LoginController, AuctionListController, BidController, SellerController
- **View** – login.fxml, auction_list.fxml, bid_screen.fxml, seller_panel.fxml
- **Network** – ServerConnection (Singleton)
- **Chart** – BidHistoryChart (JavaFX LineChart, realtime)

---

## Design Patterns áp dụng

| Pattern | Áp dụng ở đâu |
|---------|--------------|
| **Singleton** | `DatabaseConnection`, `AuctionManager`, `ServerConnection` |
| **Factory Method** | `ItemFactory` – tạo Electronics / ArtItem / Vehicle theo type |
| **Observer** | `BidObserver` – broadcast giá mới tới toàn bộ client đang xem phiên |

---

## Chức năng đã hoàn thiện

### Bắt buộc
- [x] Đăng ký / đăng nhập với 3 vai trò: Bidder, Seller, Admin
- [x] Quản lý sản phẩm: thêm / sửa / xóa (Seller)
- [x] Tạo và quản lý phiên đấu giá
- [x] Đặt giá realtime, kiểm tra hợp lệ
- [x] Tự động đóng phiên khi hết giờ
- [x] Chuyển trạng thái: OPEN → RUNNING → FINISHED → PAID/CANCELED
- [x] Xử lý ngoại lệ: AuctionClosedException, InvalidBidException, UserNotFoundException
- [x] Giao diện JavaFX đầy đủ 4 màn hình
- [x] Concurrent Bidding an toàn (ReentrantReadWriteLock)
- [x] Realtime update qua Socket broadcast (không polling)
- [x] Unit Test JUnit cho logic quan trọng
- [x] CI/CD GitHub Actions tự động chạy test khi push

### Nâng cao (bonus)
- [x] **Auto-Bidding** – đặt maxBid + increment, hệ thống tự đấu giá thay
- [x] **Anti-sniping** – tự động gia hạn phiên nếu có bid trong X giây cuối
- [x] **Bid History Visualization** – biểu đồ đường giá realtime (JavaFX LineChart)

---

## Yêu cầu môi trường

| Công cụ | Phiên bản |
|---------|-----------|
| Java | 21+ |
| Maven | 3.8+ |
| MySQL | 8.x |
| JavaFX | 21 (tự động tải qua Maven) |

---

## Cài đặt và chạy

### Bước 1 – Clone repo
```bash
git clone https://github.com/<your-username>/AuctionSystem.git
cd AuctionSystem
```

### Bước 2 – Tạo database MySQL
Mở MySQL Workbench, chạy lần lượt 2 file:
```
database/scheme.sql       ← tạo bảng
database/sample_data.sql  ← thêm dữ liệu mẫu
```

### Bước 3 – Sửa thông tin kết nối database
Mở file `server/src/main/java/com/auction/dao/DatabaseConnection.java`, sửa:
```java
private static final String PASSWORD = "password"; // đổi theo mật khẩu MySQL trên máy chạy
```

### Bước 4 – Build project
```bash
mvn compile
```

### Bước 5 – Chạy Server (Terminal 1)
```bash
mvn exec:java '-Dexec.mainClass=com.auction.Main'
```
Chờ thấy dòng: `=== Auction Server started on port 9999 ===`

### Bước 6 – Chạy Client (Terminal 2)
```bash
mvn javafx:run
```

---

## Tài khoản mẫu

| Username | Password | Role |
|----------|----------|------|
| `admin` | `password123` | ADMIN |
| `seller_alice` | `password123` | SELLER |
| `bidder_charlie` | `password123` | BIDDER |

---

## Chạy Unit Test

```bash
mvn test
```

Kết quả mong đợi:
```
Tests run: X, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## Cấu trúc thư mục

```
AuctionSystem/
├── .github/workflows/ci.yml     ← CI/CD GitHub Actions        ← Tuân
├── pom.xml                      ← Maven config                ← Tuân
├── database/
│   ├── scheme.sql               ← Tạo bảng MySQL              ← Nghĩa
│   └── sample_data.sql          ← Dữ liệu mẫu                 ← Nghĩa
├── server/src/main/java/com/auction/
│   ├── Main.java                ← Khởi động Server            ← Mạnh
│   ├── model/                   ← Entity, User, Item, Auction  ← Mạnh
│   ├── dao/                     ← Truy cập database (JDBC)    ← Nghĩa
│   ├── service/                 ← Logic nghiệp vụ             ← Nghĩa
│   ├── network/                 ← Socket Server               ← Mạnh
│   ├── manager/                 ← AuctionManager (Singleton)  ← Mạnh
│   ├── factory/                 ← ItemFactory (Factory)       ← Nghĩa
│   ├── observer/                ← BidObserver (Observer)      ← Mạnh
│   └── exception/               ← Custom Exceptions           ← Nghĩa
├── client/src/main/java/com/auction/
│   ├── MainApp.java             ← Khởi động JavaFX Client     ← Minh
│   ├── controller/              ← JavaFX Controllers (MVC)    ← Minh
│   ├── network/                 ← ServerConnection            ← Minh
│   └── chart/                   ← BidHistoryChart             ← Minh
├── client/src/main/resources/com/auction/view/
│   ├── login.fxml                                             ← Minh
│   ├── auction_list.fxml                                      ← Minh
│   ├── bid_screen.fxml                                        ← Minh
│   └── seller_panel.fxml                                      ← Minh
├── advanced/src/main/java/
│   ├── AutoBidder.java          ← Đấu giá tự động             ← Tuân
│   ├── AntiSnipingTimer.java    ← Gia hạn phiên               ← Tuân
│   └── BidHistoryVisualizer.java ← Biểu đồ lịch sử giá       ← Tuân
└── test/src/test/java/
    ├── AuctionTest.java                                       ← Tuân
    ├── BidValidationTest.java                                 ← Tuân
    ├── ItemFactoryTest.java                                   ← Tuân
    └── UserServiceTest.java                                   ← Tuân
```

---

## Giao thức Client–Server

Giao tiếp qua **JSON over TCP Socket**, mỗi message là 1 dòng.

**Request:**
```json
{ "action": "LOGIN", "data": { "username": "alice", "password": "123456" } }
{ "action": "CANCEL_AUCTION", "data": { "auctionId": "..." } }
{ "action": "MARK_AUCTION_PAID", "data": { "auctionId": "..." } }
{ "action": "REGISTER_AUTO_BID", "data": { "auctionId": "...", "maxBid": 1000000, "increment": 50000 } }
{ "action": "CANCEL_AUTO_BID", "data": { "auctionId": "..." } }
```

**Response:**
```json
{ "status": "OK",    "data": { ... }, "message": "Đăng nhập thành công" }
{ "status": "ERROR", "message": "Tên đăng nhập hoặc mật khẩu không đúng" }
```

**Broadcast realtime (server → tất cả client trong phòng):**
```json
{ "event": "NEW_BID",          "auctionId": "...", "bidAmount": 500000 }
{ "event": "AUTO_BID",         "auctionId": "...", "bidderId": "...", "amount": 550000 }
{ "event": "AUCTION_FINISHED", "auctionId": "...", "winnerId": "...", "finalPrice": 700000 }
{ "event": "AUCTION_PAID",     "auctionId": "...", "status": "PAID" }
{ "event": "AUCTION_EXTENDED", "auctionId": "...", "newEndTime": "...", "extraSeconds": 60 }
```
