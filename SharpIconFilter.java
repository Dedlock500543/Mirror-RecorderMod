package com.mirror.recorder.gui;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.ContextCapabilities;
/** Generates a complete mip chain for each bound static icon texture, including after reload. */
final class SharpIconFilter {
  static void apply() {
    try {
      int target = GL11.GL_TEXTURE_2D;
      if (GL11.glGetTexLevelParameteri(target, 1, GL11.GL_TEXTURE_WIDTH) == 0) {
        ContextCapabilities c = GLContext.getCapabilities();
        if (!c.OpenGL30 && !c.GL_ARB_framebuffer_object && !c.GL_EXT_framebuffer_object) return;
        // Minecraft normally caps SimpleTexture to level 0; lift both caps first.
        GL11.glTexParameteri(target, GL12.GL_TEXTURE_MAX_LEVEL, 1000);
        GL11.glTexParameteri(target, GL12.GL_TEXTURE_MAX_LOD, 1000);
        if (c.OpenGL30) GL30.glGenerateMipmap(target);
        else if (c.GL_ARB_framebuffer_object) ARBFramebufferObject.glGenerateMipmap(target);
        else if (c.GL_EXT_framebuffer_object) EXTFramebufferObject.glGenerateMipmapEXT(target);
        else return;
      }
      if (GL11.glGetTexLevelParameteri(target, 1, GL11.GL_TEXTURE_WIDTH) > 0) {
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
      }
    } catch (RuntimeException | LinkageError ignored) {
      // Keep Minecraft's original sampling if this driver does not support mipmaps.
    }
  }
}
