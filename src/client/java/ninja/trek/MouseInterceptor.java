
package ninja.trek;

public class MouseInterceptor {
    private static boolean intercepting = false;

    public static void setIntercepting(boolean intercept) {
        intercepting = intercept;
    }

    public static boolean isIntercepting() {
        return intercepting;
    }
}
