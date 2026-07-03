package gama.ui.display.webrtc.view;

import java.nio.ByteBuffer;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;

import gama.api.GAMA;
import gama.api.ui.displays.IDisplaySurface;
import gama.ui.display.opengl4.OpenGL;
import gama.ui.display.opengl4.renderer.IOpenGLRenderer;
import gama.ui.display.opengl4.renderer.JOGLRenderer;

public class WebRTCJOGLRenderer extends JOGLRenderer {

	public WebRTCJOGLRenderer(final WebRTCDisplaySurface surface) {
		super(surface);
	}

	@Override
	public void setDisplaySurface(final IDisplaySurface d) {
		super.setDisplaySurface(d);
		this.openGL = new WebRTCOpenGL(this);
	}

	@Override
	public void reshape(final GLAutoDrawable drawable, final int arg1, final int arg2, final int w, final int h) {
		if (w <= 0 || h <= 0) return;
		final GL4 gl = drawable.getContext().getGL().getGL4();
		getKeystoneHelper().reshape(w, h);
		openGL.reshape(gl, w, h);
		surface.getManager().forceRedrawingLayers();
		surface.updateDisplay(true);
	}

	@Override
	public boolean isNotReadyToUpdate() {
		if (surface.isDisposed() || !inited) return true;
		if (GAMA.isSynchronized()) return false;
		return getSceneHelper().isNotReadyToUpdate();
	}

	@Override
	public void display(final GLAutoDrawable drawable) {
		super.display(drawable);
	}

	public byte[] capturePixels(final GLAutoDrawable drawable) {
		if (drawable == null) return null;
		final GL4 gl = drawable.getGL().getGL4();
		if (gl == null) return null;
		final int w = drawable.getSurfaceWidth();
		final int h = drawable.getSurfaceHeight();
		if (w <= 0 || h <= 0) return null;
		final ByteBuffer buffer = Buffers.newDirectByteBuffer(w * h * 4);
		gl.glReadPixels(0, 0, w, h, GL4.GL_RGBA, GL4.GL_UNSIGNED_BYTE, buffer);
		final byte[] pixels = new byte[w * h * 4];
		buffer.get(pixels);
		return pixels;
	}

	private static class WebRTCOpenGL extends OpenGL {
		public WebRTCOpenGL(final IOpenGLRenderer renderer) {
			super(renderer);
		}

		@Override
		public double[] getPixelWidthAndHeightOfWorld() {
			double[] res = super.getPixelWidthAndHeightOfWorld();
			if (Double.isNaN(res[0]) || Double.isNaN(res[1]) || res[0] <= 0 || res[1] <= 0) {
				return new double[] { getRenderer().getSurface().getWidth(), getRenderer().getSurface().getHeight(), 0, 0 };
			}
			return res;
		}
	}
}