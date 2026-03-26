public abstract class Shape {
    protected int x, y;

    public Shape(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public abstract void draw();
    public abstract void erase();

    public void moveTo(int newX, int newY) {
        erase();
        this.x = newX;
        this.y = newY;
        draw();
    }

}   
