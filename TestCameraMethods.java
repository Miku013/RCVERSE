import net.minecraft.client.Camera;
import java.lang.reflect.Method;

public class TestCameraMethods {
    public static void main(String[] args) {
        System.out.println("Methods in Camera:");
        for (Method m : Camera.class.getDeclaredMethods()) {
            System.out.println(m.getName() + " -> " + java.util.Arrays.toString(m.getParameterTypes()));
        }
    }
}
