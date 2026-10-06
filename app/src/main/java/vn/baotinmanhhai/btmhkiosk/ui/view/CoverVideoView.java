package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.io.FileInputStream;

/**
 * Phat video tu res/raw (banner Home) hoac tep local (quang cao man cho), tat tieng. Che do "cover" phu kin
 * khung (cat phan thua — giong FitBannerVideo cua WinForms); che do "fit" vua khung, giu ti le. Co the phat lap
 * vo han (banner) hoac phat mot lan roi bao onCompleted (quang cao). Surface tu huy khi view bi an, nen player
 * duoc tao lai khi hien lai.
 */
public class CoverVideoView extends TextureView implements TextureView.SurfaceTextureListener {

    public interface Events {
        void onCompleted();

        void onError(String message);
    }

    private static final String TAG = "CoverVideoView";

    private int rawRes = 0;
    private File file;
    private boolean cover = true;
    private boolean looping = true;
    private Events events;

    private SurfaceTexture texture;
    private MediaPlayer player;
    private Surface surface;
    private int videoW;
    private int videoH;

    public CoverVideoView(Context context) {
        super(context);
        setSurfaceTextureListener(this);
    }

    /** Video banner Home: res/raw, phu kin khung, lap vo han. */
    public CoverVideoView(Context context, int rawRes) {
        this(context);
        this.rawRes = rawRes;
    }

    public void setCover(boolean cover) {
        this.cover = cover;
        applyScale();
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public void setEvents(Events events) {
        this.events = events;
    }

    /** Phat tep local tu dau (quang cao). */
    public void playFile(File file) {
        this.file = file;
        this.rawRes = 0;
        this.videoW = 0;
        this.videoH = 0;
        if (texture != null) startPlayer(texture);
    }

    public void stopPlayback() {
        releasePlayer();
    }

    private void startPlayer(SurfaceTexture st) {
        releasePlayer();
        if (rawRes == 0 && file == null) return;
        try {
            surface = new Surface(st);
            MediaPlayer mp = new MediaPlayer();
            if (file != null) {
                try (FileInputStream in = new FileInputStream(file)) {
                    mp.setDataSource(in.getFD());
                }
            } else {
                try (AssetFileDescriptor afd = getResources().openRawResourceFd(rawRes)) {
                    mp.setDataSource(afd);
                }
            }
            mp.setSurface(surface);
            mp.setLooping(looping);
            mp.setVolume(0f, 0f);
            mp.setOnVideoSizeChangedListener((m, w, h) -> {
                videoW = w;
                videoH = h;
                applyScale();
            });
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.setOnCompletionListener(m -> {
                Events e = events;
                if (e != null && !looping) e.onCompleted();
            });
            mp.setOnErrorListener((m, what, extra) -> {
                Log.w(TAG, "Loi phat video: what=" + what + " extra=" + extra);
                Events e = events;
                if (e != null) e.onError("what=" + what + " extra=" + extra);
                return true;
            });
            mp.prepareAsync();
            player = mp;
        } catch (Exception e) {
            Log.w(TAG, "Khong mo duoc video: " + e.getMessage());
            releasePlayer();
            Events ev = events;
            if (ev != null) ev.onError(String.valueOf(e.getMessage()));
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }

    /** TextureView mac dinh keo gian video vua khung; scale lai de giu ti le (cover: phu kin, fit: vua khung). */
    private void applyScale() {
        int viewW = getWidth();
        int viewH = getHeight();
        if (viewW <= 0 || viewH <= 0 || videoW <= 0 || videoH <= 0) return;
        float sw = (float) viewW / videoW;
        float sh = (float) viewH / videoH;
        float scale = cover ? Math.max(sw, sh) : Math.min(sw, sh);
        Matrix matrix = new Matrix();
        matrix.setScale(videoW * scale / viewW, videoH * scale / viewH, viewW / 2f, viewH / 2f);
        setTransform(matrix);
    }

    /** Tam dung khi Activity roi nen (tiet kiem giai ma 4K). */
    public void pause() {
        if (player != null) {
            try {
                player.pause();
            } catch (Exception ignored) {
            }
        }
    }

    public void resume() {
        if (player != null) {
            try {
                player.start();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyScale();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
        texture = st;
        startPlayer(st);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
        applyScale();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        texture = null;
        releasePlayer();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {
    }
}
