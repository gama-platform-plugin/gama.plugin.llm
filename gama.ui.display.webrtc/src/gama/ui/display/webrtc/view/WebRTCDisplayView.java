package gama.ui.display.webrtc.view;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import gama.api.GAMA;
import gama.api.ui.IGui;
import gama.ui.experiment.views.displays.LayeredDisplayView;

public class WebRTCDisplayView extends LayeredDisplayView {

	public static final String ID = "gama.ui.application.view.OpenGLDisplayView.WebRTC";

	@Override
	public WebRTCDisplaySurface getDisplaySurface() { return (WebRTCDisplaySurface) super.getDisplaySurface(); }

	@Override
	protected Composite createSurfaceComposite(final Composite parent) {
		final WebRTCDisplaySurface surface =
				(WebRTCDisplaySurface) GAMA.getGui().createDisplaySurfaceFor(getOutput(), parent);
		surfaceComposite = new Composite(parent, org.eclipse.swt.SWT.NONE);
		surface.outputReloaded();
		return surfaceComposite;
	}

	@Override
	public boolean isOpenGL() { return true; }

	@Override
	public Control[] getZoomableControls() {
		return new Control[] { surfaceComposite };
	}

	@Override
	public Control getInteractionControl() { return surfaceComposite; }

	@Override
	public boolean forceOverlayVisibility() { return false; }

	@Override
	public void hideCanvas() {
	}

	@Override
	public void showCanvas() {
	}

	@Override
	public void focusCanvas() {
	}

	@Override
	public boolean isCameraLocked() { return false; }

	@Override
	public boolean isCameraDynamic() { return false; }

	@Override
	public boolean hasCameras() { return true; }

	@Override
	public boolean is2D() { return false; }

	@Override
	public boolean largePauseIcon() { return true; }

	@Override
	public void setFocus() {
	}
}
