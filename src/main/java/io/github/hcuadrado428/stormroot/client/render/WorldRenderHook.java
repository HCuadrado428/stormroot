package io.github.hcuadrado428.stormroot.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.hcuadrado428.stormroot.client.sky.SkyRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

//? if >=1.21 {
import com.mojang.blaze3d.systems.RenderSystem;
//?}

/**
 * Único punto desde el que Stormroot dibuja en el mundo (sin Bloomberto). Cada loader lo llama desde su evento de
 * render del mundo (ver {@code StormrootClient}). Mismo método que {@code WorldRenderBridge} de Bloomberto, probado en
 * todas las versiones:
 *
 * <p>Desde 1.20.5 la rotación de la cámara ya no va en el {@code PoseStack} del nivel sino en la matriz model-view de
 * {@code RenderSystem}, y cada loader lanza su evento en un momento distinto (Fabric cuando esa matriz ya se ha quitado;
 * Forge/NeoForge cuando aún está). Para que sea igual en todos, en 1.21+ se FIJA esa matriz a la vista de la cámara
 * mientras se dibuja, y se dibuja con un {@code PoseStack} identidad y coordenadas relativas a la cámara.
 */
public final class WorldRenderHook {
    private WorldRenderHook() {
    }

    /**
     * @param poseStack el del loader (1.20.1: ya lleva la rotación de la cámara; 1.21+: identidad)
     * @param view      1.21+: la matriz de vista que da el loader, o {@code null} para sacarla de la cámara
     */
    public static void fire(PoseStack poseStack, Camera camera, float partialTick, Matrix4f view) {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        //? if >=1.21.11 {
        // 1.21.11 ya no tiene applyModelViewMatrix: los dibujos leen la pila model-view directamente.
        org.joml.Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(view != null ? view : new Matrix4f().rotation(camera.rotation().conjugate(new org.joml.Quaternionf())));
        try {
            SkyRenderer.render(poseStack, camera, partialTick, buffers);
            buffers.endBatch();
        } finally {
            modelView.popMatrix();
        }
        //?} else if >=1.21 {
        /*org.joml.Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(view != null ? view : new Matrix4f().rotation(camera.rotation().conjugate(new org.joml.Quaternionf())));
        RenderSystem.applyModelViewMatrix();
        try {
            SkyRenderer.render(poseStack, camera, partialTick, buffers);
            buffers.endBatch();
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
        *///?} else {
        /*SkyRenderer.render(poseStack, camera, partialTick, buffers);
        buffers.endBatch();
        *///?}
    }
}
