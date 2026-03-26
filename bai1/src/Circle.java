public class Circle extends Shape {
    public Circle(int x, int y) {
        super(x, y);   
    }

    public void erase() {
        System.out.println("Xóa hình tròn tại (" + x + "," + y + ")");
    }
    public void draw() {
        System.out.println("Vẽ hình tròn tại (" + x + "," + y + ")");
    }

    public static void main(String[] args) {
        Shape circle = new Circle(10, 10); 
        circle.moveTo(20, 20);
    }
}
