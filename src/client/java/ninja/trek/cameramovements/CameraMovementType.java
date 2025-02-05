package ninja.trek.cameramovements;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CameraMovementType {
    String name() default "";
    String description() default "";
    boolean enabled() default true;
}