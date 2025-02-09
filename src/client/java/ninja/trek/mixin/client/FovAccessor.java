package ninja.trek.mixin.client;

public interface FovAccessor {
    void setCustomFov(float fov);
    float getCustomFov();
    void setZoom(float zoom, float x, float y);
}