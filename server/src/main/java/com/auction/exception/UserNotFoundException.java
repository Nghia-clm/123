package com.auction.exception;

/*
 Ngoại lệ này được ném ra khi việc tìm kiếm Người dùng (theo ID hoặc tên người dùng) không trả về kết quả nào.
 */
public class UserNotFoundException extends Exception {

    private final String identifier; // the ID or username that was looked up

    public UserNotFoundException(String message) {
        super(message);
        this.identifier = null;
    }

    public UserNotFoundException(String identifier, String message) {
        super(message);
        this.identifier = identifier;
    }

    public UserNotFoundException(String identifier, String message, Throwable cause) {
        super(message, cause);
        this.identifier = identifier;
    }

    /*
     Nhà máy tiện ích: tạo ra một thông báo mô tả chỉ từ mã định danh.
     @param Mã định danh là tên người dùng hoặc ID người dùng không được tìm thấy.
     @return a new UserNotFoundException
     */
    public static UserNotFoundException forIdentifier(String identifier) {
        return new UserNotFoundException(
                identifier,
                "User not found: '" + identifier + "'.");
    }

    /*
     @return Tên người dùng hoặc ID đã gây ra lỗi này, hoặc trả về null nếu không được chỉ định.
    */
    public String getIdentifier() {
        return identifier;
    }
}
