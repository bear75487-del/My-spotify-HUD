package dev.spotifyhud.hud;

/** Position and size of the HUD in scaled screen coordinates. */
public record HudBounds(int x, int y, int width, int height) {
    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= this.x
                && mouseX < this.x + this.width
                && mouseY >= this.y
                && mouseY < this.y + this.height;
    }
}
