package com.github.wallev.maidsoulkitchen.modclazzchecker.manager;

import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import com.github.wallev.maidsoulkitchen.foundation.utility.Mods;
import com.github.wallev.maidsoulkitchen.modclazzchecker.core.classana.IMskMixinInterface;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Method;

/**
 * Loads the curated compatibility manifest and validates only the tasks that
 * are enabled for the installed mod set.
 *
 * <p>The manifest is also read during Mixin selection, but expensive class
 * validation runs once from the normal mod bootstrap. No classpath scan or
 * build-time bytecode generator is involved.</p>
 */
public final class CompatibilityRegistry {
    static final String MANIFEST_FILE = "mod_task_clazz.json";
    private static final int ASM_VERSION = Opcodes.ASM9;

    private static final Map<String, ClassMetadata> CLASS_CACHE = new HashMap<>();

    private static volatile Manifest manifest;
    private static volatile RuntimeException manifestFailure;
    private static volatile boolean initialized;
    private static volatile Map<String, Boolean> taskCompatibility = Map.of();
    private static volatile List<Failure> failures = List.of();

    private CompatibilityRegistry() {
    }

    /** Load the lightweight Mixin gate without validating task classes. */
    public static synchronized void prepareMixinGate() {
        if (manifest != null || manifestFailure != null) {
            return;
        }

        try {
            Path resource = LoadingModList.get()
                    .getModFileById(MaidsoulKitchen.MOD_ID)
                    .getFile()
                    .findResource(MANIFEST_FILE);
            manifest = parseManifest(Files.readString(resource));
        } catch (Exception exception) {
            manifestFailure = new IllegalStateException(
                    "Unable to load " + MANIFEST_FILE, exception);
            MaidsoulKitchen.LOGGER.error("Failed to load compatibility manifest", exception);
        }
    }

    /** Validate enabled task integrations exactly once. */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        prepareMixinGate();
        Map<String, Boolean> results = new LinkedHashMap<>();
        List<Failure> detectedFailures = new ArrayList<>();

        if (manifestFailure != null || manifest == null) {
            for (TaskInfo task : TaskInfo.VALUES) {
                if (task != TaskInfo.NONE && task.canLoadWithoutCheckClazz()) {
                    results.put(task.getUidStr(), false);
                    detectedFailures.add(new Failure(task.getUidStr(), task.getBindMod().getModId(),
                            List.of("compatibility manifest could not be loaded")));
                }
            }
        } else {
            for (TaskInfo task : TaskInfo.VALUES) {
                if (task == TaskInfo.NONE || !task.canLoadWithoutCheckClazz()) {
                    continue;
                }

                Entry entry = manifest.tasks().get(task.getUidStr());
                List<String> issues = new ArrayList<>();
                if (entry == null) {
                    issues.add("task is missing from compatibility manifest");
                } else {
                    if (entry.bindMod() != task.getBindMod()) {
                        issues.add("manifest bindMod is " + entry.bindMod().name()
                                + ", expected " + task.getBindMod().name());
                    }
                    validateEntry(entry, issues);
                    validateAppliedMixins(task.getUidStr(), issues);
                }

                boolean compatible = issues.isEmpty();
                results.put(task.getUidStr(), compatible);
                if (!compatible) {
                    Failure failure = new Failure(task.getUidStr(), task.getBindMod().getModId(),
                            List.copyOf(issues));
                    detectedFailures.add(failure);
                    MaidsoulKitchen.LOGGER.error("Compatibility check failed for {} ({}): {}",
                            failure.taskUid(), failure.modId(), String.join("; ", failure.issues()));
                }
            }
        }

        taskCompatibility = Collections.unmodifiableMap(results);
        failures = List.copyOf(detectedFailures);
        initialized = true;
    }

    public static boolean isTaskCompatible(String taskUid) {
        initialize();
        return taskCompatibility.getOrDefault(taskUid, false);
    }

    /**
     * Return whether compatibility mixins targeting this class may be applied.
     * Targets not described by the manifest are MSK/TLM mixins and remain on.
     */
    public static boolean canApplyMixin(String targetClassName) {
        prepareMixinGate();
        if (manifest == null) {
            return false;
        }
        List<Mods> requirements = manifest.mixinRequirements().get(targetClassName);
        return requirements == null || requirements.stream().allMatch(Mods::versionLoad);
    }

    public static List<Failure> failures() {
        initialize();
        return failures;
    }

    static Manifest manifestForTests() {
        return manifest;
    }

    static Manifest parseManifest(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, Entry> tasks = new LinkedHashMap<>();

        JsonObject taskObject = requiredObject(root, "clazzInfoMap");
        for (Map.Entry<String, JsonElement> taskElement : taskObject.entrySet()) {
            JsonObject value = taskElement.getValue().getAsJsonObject();
            Mods bindMod = Mods.by(value.get("bindMod").getAsString());
            JsonObject classInfo = requiredObject(value, "clazzInfo");
            tasks.put(taskElement.getKey(), new Entry(
                    bindMod,
                    stringList(classInfo, "classes"),
                    stringList(classInfo, "methods"),
                    stringList(classInfo, "fields")
            ));
        }

        Map<String, LinkedHashSet<Mods>> targetRequirements = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> taskMixins = new LinkedHashMap<>();
        JsonObject mixinList = requiredObject(requiredObject(root, "mixinInfo"), "list");
        for (Map.Entry<String, JsonElement> taskElement : mixinList.entrySet()) {
            for (JsonElement mixinElement : taskElement.getValue().getAsJsonArray()) {
                JsonObject mixin = mixinElement.getAsJsonObject();
                String taskUid = mixin.get("taskUid").getAsString();
                Mods compatMod = Mods.by(mixin.get("compatMod").getAsString());
                for (String target : stringList(mixin, "mixinList")) {
                    targetRequirements.computeIfAbsent(target, ignored -> new LinkedHashSet<>())
                            .add(compatMod);
                    taskMixins.computeIfAbsent(taskUid, ignored -> new LinkedHashSet<>())
                            .add(target);
                }
            }
        }

        return new Manifest(
                Collections.unmodifiableMap(tasks),
                immutableListMap(targetRequirements),
                immutableListMap(taskMixins)
        );
    }

    private static <T> Map<String, List<T>> immutableListMap(
            Map<String, ? extends Set<T>> source) {
        Map<String, List<T>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(result);
    }

    private static JsonObject requiredObject(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Missing object: " + name);
        }
        return element.getAsJsonObject();
    }

    private static List<String> stringList(JsonObject parent, String name) {
        JsonArray array = parent.getAsJsonArray(name);
        if (array == null) {
            throw new IllegalArgumentException("Missing array: " + name);
        }
        List<String> result = new ArrayList<>(array.size());
        array.forEach(value -> result.add(value.getAsString()));
        return List.copyOf(result);
    }

    private static void validateEntry(Entry entry, List<String> issues) {
        for (String className : entry.classes()) {
            if (readClass(className) == null) {
                issues.add("missing class " + className);
            }
        }

        for (String signature : entry.methods()) {
            int separator = signature.indexOf('#');
            if (separator < 1 || separator == signature.length() - 1) {
                issues.add("invalid method signature " + signature);
                continue;
            }
            String className = signature.substring(0, separator);
            String method = signature.substring(separator + 1);
            ClassMetadata metadata = readClass(className);
            if ((metadata == null || !allMethods(className).contains(method))
                    && !hasRuntimeMethod(className, method)) {
                issues.add("missing method " + signature);
            }
        }

        for (String signature : entry.fields()) {
            int separator = signature.indexOf('#');
            if (separator < 1 || separator == signature.length() - 1) {
                issues.add("invalid field signature " + signature);
                continue;
            }
            String className = signature.substring(0, separator);
            String field = signature.substring(separator + 1);
            ClassMetadata metadata = readClass(className);
            if (metadata == null || !allFields(className).contains(field)) {
                issues.add("missing field " + signature);
            }
        }
    }

    private static void validateAppliedMixins(String taskUid, List<String> issues) {
        if (manifest == null) {
            return;
        }
        for (String targetName : manifest.mixinsByTask().getOrDefault(taskUid, List.of())) {
            try {
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                Class<?> target = Class.forName(targetName, false, loader);
                if (!IMskMixinInterface.class.isAssignableFrom(target)) {
                    issues.add("mixin marker missing from " + targetName);
                }
            } catch (ClassNotFoundException | LinkageError exception) {
                issues.add("mixin target unavailable " + targetName + " ("
                        + exception.getClass().getSimpleName() + ")");
            }
        }
    }

    private static Set<String> allMethods(String rootClass) {
        Set<String> methods = new HashSet<>();
        walkHierarchy(rootClass, metadata -> methods.addAll(metadata.methods()));
        return methods;
    }

    private static Set<String> allFields(String rootClass) {
        Set<String> fields = new HashSet<>();
        walkHierarchy(rootClass, metadata -> fields.addAll(metadata.fields()));
        return fields;
    }

    /**
     * Resource streams contain pre-Mixin bytecode. Recheck a missing method on
     * the transformed runtime class so accessor methods injected by MSK are not
     * reported as absent.
     */
    private static boolean hasRuntimeMethod(String rootClass, String signature) {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> root = Class.forName(rootClass, false, loader);
            Deque<Class<?>> remaining = new ArrayDeque<>();
            Set<Class<?>> visited = new HashSet<>();
            remaining.add(root);
            while (!remaining.isEmpty()) {
                Class<?> current = remaining.removeFirst();
                if (!visited.add(current)) {
                    continue;
                }
                for (Method method : current.getDeclaredMethods()) {
                    if ((method.getName() + Type.getMethodDescriptor(method)).equals(signature)) {
                        return true;
                    }
                }
                if (current.getSuperclass() != null) {
                    remaining.addLast(current.getSuperclass());
                }
                Collections.addAll(remaining, current.getInterfaces());
            }
        } catch (ClassNotFoundException | LinkageError | SecurityException exception) {
            MaidsoulKitchen.LOGGER.debug("Unable to inspect transformed class {}", rootClass, exception);
        }
        return false;
    }

    private static void walkHierarchy(String rootClass, MetadataConsumer consumer) {
        Deque<String> remaining = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        remaining.add(rootClass);
        while (!remaining.isEmpty()) {
            String className = remaining.removeFirst();
            if (!visited.add(className)) {
                continue;
            }
            ClassMetadata metadata = readClass(className);
            if (metadata == null) {
                continue;
            }
            consumer.accept(metadata);
            if (metadata.superName() != null) {
                remaining.addLast(metadata.superName());
            }
            remaining.addAll(metadata.interfaces());
        }
    }

    private static synchronized ClassMetadata readClass(String className) {
        if (CLASS_CACHE.containsKey(className)) {
            return CLASS_CACHE.get(className);
        }

        String resourceName = className.replace('.', '/') + ".class";
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream stream = loader.getResourceAsStream(resourceName)) {
            if (stream == null) {
                CLASS_CACHE.put(className, null);
                return null;
            }
            ClassMetadata metadata = readClassMetadata(stream);
            CLASS_CACHE.put(className, metadata);
            return metadata;
        } catch (IOException | RuntimeException exception) {
            MaidsoulKitchen.LOGGER.debug("Unable to inspect class {}", className, exception);
            CLASS_CACHE.put(className, null);
            return null;
        }
    }

    private static ClassMetadata readClassMetadata(InputStream stream) throws IOException {
        Set<String> methods = new LinkedHashSet<>();
        Set<String> fields = new LinkedHashSet<>();
        List<String> hierarchy = new ArrayList<>();
        String[] superName = new String[1];

        ClassReader reader = new ClassReader(stream);
        reader.accept(new ClassVisitor(ASM_VERSION) {
            @Override
            public void visit(int version, int access, String name, String signature,
                              String parent, String[] interfaces) {
                superName[0] = parent == null ? null : parent.replace('/', '.');
                if (interfaces != null) {
                    for (String interfaceName : interfaces) {
                        hierarchy.add(interfaceName.replace('/', '.'));
                    }
                }
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                methods.add(name + descriptor);
                return null;
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                                           String signature, Object value) {
                fields.add(name);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        return new ClassMetadata(Set.copyOf(methods), Set.copyOf(fields),
                superName[0], List.copyOf(hierarchy));
    }

    public record Failure(String taskUid, String modId, List<String> issues) {
    }

    record Manifest(Map<String, Entry> tasks,
                    Map<String, List<Mods>> mixinRequirements,
                    Map<String, List<String>> mixinsByTask) {
    }

    record Entry(Mods bindMod, List<String> classes, List<String> methods,
                 List<String> fields) {
    }

    private record ClassMetadata(Set<String> methods, Set<String> fields,
                                 String superName, List<String> interfaces) {
    }

    @FunctionalInterface
    private interface MetadataConsumer {
        void accept(ClassMetadata metadata);
    }
}
