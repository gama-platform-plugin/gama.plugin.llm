package gama.ui.display.webrtc.view;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLProfile;

import gama.annotations.display;
import gama.annotations.doc;
import gama.api.ui.IOutput;

import gama.ui.display.opengl4.renderer.JOGLRenderer;
import gama.ui.display.opengl4.view.SWTOpenGLDisplaySurface;
import gama.ui.display.webrtc.WebRTCSessionManager;
import gama.ui.shared.utils.WorkbenchHelper;

@display(value = { "webrtc" }, viewId = WebRTCDisplayView.ID, is3D = true)
@doc("Displays that renders the 3D scene headlessly and streams it via WebRTC")
public class WebRTCDisplaySurface extends SWTOpenGLDisplaySurface {

	private static final int FPS = 30;
	private static final int FB_WIDTH = 1920;
	private static final int FB_HEIGHT = 1080;

	WebRTCJOGLRenderer webRTCRenderer;
	ScheduledExecutorService captureTimer;
	ExecutorService frameProcessor;
	GLAutoDrawable offscreenDrawable;
	private final String displayId = java.util.UUID.randomUUID().toString();
	private volatile boolean framePending;

	public WebRTCDisplaySurface(final IOutput.Display output, final Object parent) {
		super(output, parent);
		this.webRTCRenderer = (WebRTCJOGLRenderer) getIGraphics();
		initWebRTC();
		startCaptureTimer();
	}

	private void initWebRTC() {
		try {
			if (getCanvas() != null) {
				getCanvas().removeGLEventListener(webRTCRenderer);
				final var anim = getCanvas().getAnimator();
				if (anim != null) {
					anim.stop();
				}
			}

			final var profile = GLProfile.get(GLProfile.GL4);
			final var caps = new GLCapabilities(profile);
			caps.setDoubleBuffered(true);
			caps.setHardwareAccelerated(true);
			caps.setDepthBits(24);
			caps.setAlphaBits(8);
			caps.setOnscreen(false);
			offscreenDrawable = GLDrawableFactory.getFactory(profile)
				.createOffscreenAutoDrawable(null, caps, null, FB_WIDTH, FB_HEIGHT);
			offscreenDrawable.setAutoSwapBufferMode(false);
			offscreenDrawable.addGLEventListener(webRTCRenderer);
			offscreenDrawable.display();
			webRTCRenderer.reshape(offscreenDrawable, 0, 0, FB_WIDTH, FB_HEIGHT);
			final var initCtx = offscreenDrawable.getContext();
			if (initCtx != null) {
				initCtx.makeCurrent();
				try {
					webRTCRenderer.display(offscreenDrawable);
				} finally {
					initCtx.release();
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		WebRTCSessionManager.getInstance();
	}

	private void startCaptureTimer() {
		captureTimer = Executors.newSingleThreadScheduledExecutor(r -> {
			final Thread t = new Thread(r, "WebRTC-Capture");
			t.setDaemon(true);
			return t;
		});
		frameProcessor = Executors.newSingleThreadExecutor(r -> {
			final Thread t = new Thread(r, "WebRTC-Frame");
			t.setDaemon(true);
			return t;
		});
		captureTimer.scheduleAtFixedRate(this::captureFrame, 1000, 1000 / FPS, TimeUnit.MILLISECONDS);
	}

	private void captureFrame() {
		if (framePending || offscreenDrawable == null) return;
		framePending = true;
		WorkbenchHelper.run(() -> {
			try {
				final var ctx = offscreenDrawable.getContext();
				if (ctx == null) { framePending = false; return; }
				ctx.makeCurrent();
				try {
					webRTCRenderer.display(offscreenDrawable);
					final byte[] p = webRTCRenderer.capturePixels(offscreenDrawable);
					if (p != null) {
						final int w = offscreenDrawable.getSurfaceWidth();
						final int h = offscreenDrawable.getSurfaceHeight();
						frameProcessor.execute(() -> {
							try {
								WebRTCSessionManager.getInstance().broadcastFrame(displayId, p, w, h);
							} finally {
								framePending = false;
							}
						});
					} else {
						framePending = false;
					}
				} finally {
					ctx.release();
				}
			} catch (final Exception e) {
				framePending = false;
				e.printStackTrace();
			}
		});
	}

	@Override
	public int getWidth() { return offscreenDrawable != null ? offscreenDrawable.getSurfaceWidth() : FB_WIDTH; }

	@Override
	public int getHeight() { return offscreenDrawable != null ? offscreenDrawable.getSurfaceHeight() : FB_HEIGHT; }

	@Override
	protected JOGLRenderer createRenderer() {
		return new WebRTCJOGLRenderer(this);
	}

	@Override
	public void dispose() {
		if (super.isDisposed()) return;
		WebRTCSessionManager.getInstance().removeDisplay(displayId);
		if (captureTimer != null) { captureTimer.shutdown(); }
		if (frameProcessor != null) { frameProcessor.shutdown(); }
		if (offscreenDrawable != null) { offscreenDrawable.destroy(); }
		super.dispose();
	}
}