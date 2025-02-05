package ninja.trek;

import ninja.trek.cameramovements.CameraMovementType;
import ninja.trek.cameramovements.ICameraMovement;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class CameraMovementRegistry {
    private static final List<Class<? extends ICameraMovement>> movementTypes = new ArrayList<>();
    private static int currentTypeIndex = 0;
    private static final String BASE_PACKAGE = "ninja.trek.cameramovements";

    public static void initialize() {
        try {
            scanPackage(BASE_PACKAGE);
        } catch (Exception e) {
            Craneshot.LOGGER.error("Failed to scan for camera movements", e);
        }
    }

    private static void scanPackage(String packageName) {
        try {
            String path = packageName.replace('.', '/');
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = classLoader.getResources(path);

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();

                if (protocol.equals("file")) {
                    scanDirectory(new File(resource.getFile()), packageName);
                } else if (protocol.equals("jar")) {
                    scanJar(resource, path);
                }
            }
        } catch (IOException e) {
            Craneshot.LOGGER.error("Error scanning package: " + packageName, e);
        }
    }

    private static void scanDirectory(File directory, String packageName) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    scanDirectory(file, packageName + "." + file.getName());
                } else if (file.getName().endsWith(".class")) {
                    String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                    processClass(className);
                }
            }
        }
    }

    private static void scanJar(URL resourceUrl, String path) {
        String jarPath = resourceUrl.getPath().substring(5, resourceUrl.getPath().indexOf("!"));
        try (JarFile jarFile = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.startsWith(path) && entryName.endsWith(".class")) {
                    String className = entryName.substring(0, entryName.length() - 6).replace('/', '.');
                    processClass(className);
                }
            }
        } catch (IOException e) {
            Craneshot.LOGGER.error("Error scanning JAR file: " + jarPath, e);
        }
    }

    private static void processClass(String className) {
        try {
            Class<?> cls = Class.forName(className);
            if (ICameraMovement.class.isAssignableFrom(cls) &&
                    cls.isAnnotationPresent(CameraMovementType.class)) {

                CameraMovementType annotation = cls.getAnnotation(CameraMovementType.class);
                if (annotation.enabled()) {
                    @SuppressWarnings("unchecked")
                    Class<? extends ICameraMovement> movementClass = (Class<? extends ICameraMovement>) cls;
                    registerMovement(movementClass);
                }
            }
        } catch (ClassNotFoundException e) {
            Craneshot.LOGGER.error("Error loading class: " + className, e);
        }
    }

    public static void registerMovement(Class<? extends ICameraMovement> movementClass) {
        if (!movementTypes.contains(movementClass)) {
            movementTypes.add(movementClass);
        }
    }

    public static ICameraMovement createCurrentMovement() {
        try {
            Constructor<? extends ICameraMovement> constructor = movementTypes.get(currentTypeIndex).getDeclaredConstructor();
            return constructor.newInstance();
        } catch (Exception e) {
            Craneshot.LOGGER.error("Failed to create movement instance", e);
            return null;
        }
    }

    public static List<MovementInfo> getAllMovements() {
        List<MovementInfo> movements = new ArrayList<>();
        for (Class<? extends ICameraMovement> cls : movementTypes) {
            CameraMovementType annotation = cls.getAnnotation(CameraMovementType.class);
            if (annotation != null) {
                movements.add(new MovementInfo(
                        annotation.name().isEmpty() ? cls.getSimpleName() : annotation.name(),
                        annotation.description(),
                        cls
                ));
            }
        }
        return movements;
    }

    public static void cycleNextMovement() {
        currentTypeIndex = (currentTypeIndex + 1) % movementTypes.size();
    }

    public static String getCurrentMovementName() {
        Class<? extends ICameraMovement> currentClass = movementTypes.get(currentTypeIndex);
        CameraMovementType annotation = currentClass.getAnnotation(CameraMovementType.class);
        return annotation != null && !annotation.name().isEmpty() ?
                annotation.name() : currentClass.getSimpleName();
    }

    public static int getMovementCount() {
        return movementTypes.size();
    }

    // Helper class to hold movement type information
    public static class MovementInfo {
        private final String name;
        private final String description;
        private final Class<? extends ICameraMovement> movementClass;

        public MovementInfo(String name, String description, Class<? extends ICameraMovement> movementClass) {
            this.name = name;
            this.description = description;
            this.movementClass = movementClass;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public Class<? extends ICameraMovement> getMovementClass() { return movementClass; }
    }
}