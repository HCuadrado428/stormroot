package io.github.hcuadrado428.stormroot.client.compat;

import io.github.hcuadrado428.stormroot.Stormroot;

/**
 * Iris (opcional, por reflexión: no hace falta para compilar). Con un pack de shaders, Iris solo dibuja las
 * "pipelines" de render que sabe con qué programa del pack pintar; la de {@code debugQuads}, con la que
 * {@code SkyRenderer} dibuja nubes, sombras, aurora y muro de arena, no la conoce y no se veía nada. Desde 1.21.5
 * Iris tiene {@code IrisApi#assignPipeline(RenderPipeline, IrisProgram)} para decírselo.
 */
public final class IrisCompat {
    /** Programas del pack por orden de preferencia: el básico (posición + color) es el que encaja. */
    private static final String[] PROGRAMS = {"BASIC", "TEXTURED", "CLOUDS"};

    private static boolean tried;
    private static String assigned;

    private IrisCompat() {
    }

    /** Programa de Iris asignado al cielo (null si no hay Iris o no se pudo). */
    public static String assignedProgram() {
        return assigned;
    }

    /** ¿Hay un pack de shaders de Iris encendido? (false sin Iris). */
    public static boolean shadersInUse() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object iris = api.getMethod("getInstance").invoke(null);
            return (Boolean) api.getMethod("isShaderPackInUse").invoke(iris);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Se llama al dibujar el cielo; solo hace algo la primera vez. */
    public static void registerSkyPipeline() {
        if (tried) {
            return;
        }
        tried = true;
        //? if >=1.21.11 {
        Class<?> api;
        try {
            api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
        } catch (ClassNotFoundException e) {
            return; // sin Iris
        }
        try {
            Object iris = api.getMethod("getInstance").invoke(null);
            Class<?> programClass = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Object[] programs = programClass.getEnumConstants();
            Object chosen = null;
            // -Dstormroot.irisProgram=NOMBRE para probar otro programa.
            String forced = System.getProperty("stormroot.irisProgram");
            for (String name : forced != null ? new String[] {forced} : PROGRAMS) {
                for (Object p : programs) {
                    if (chosen == null && ((Enum<?>) p).name().equals(name)) {
                        chosen = p;
                    }
                }
            }
            Stormroot.LOGGER.info("[stormroot] Iris: programas {}; el cielo usa {}", java.util.Arrays.toString(programs), chosen);
            if (chosen == null) {
                return;
            }
            api.getMethod("assignPipeline", com.mojang.blaze3d.pipeline.RenderPipeline.class, programClass)
                    .invoke(iris, net.minecraft.client.renderer.RenderPipelines.DEBUG_QUADS, chosen);
            assigned = ((Enum<?>) chosen).name();
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Otro mod ya se la asignó: vale igual.
            Stormroot.LOGGER.warn("[stormroot] Iris no aceptó la pipeline del cielo: {}", String.valueOf(e.getCause()));
        } catch (Throwable t) {
            Stormroot.LOGGER.warn("[stormroot] Esta versión de Iris no permite asignar la pipeline del cielo: {}", t.toString());
        }
        //?}
    }
}
