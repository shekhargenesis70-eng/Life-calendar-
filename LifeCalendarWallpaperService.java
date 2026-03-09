package com.lifecalendar;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import java.util.Calendar;
import java.util.TimeZone;

public class LifeCalendarWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() { return new CalendarEngine(); }

    class CalendarEngine extends Engine {

        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable drawRunnable = this::draw;
        private boolean visible = true;
        private float pulse = 1f, pulseDir = 0.02f;

        private Paint bgP, doneP, todayP, leftP, clockP, dateP, statsP, barBgP, barP;

        @Override
        public void onCreate(SurfaceHolder s) {
            super.onCreate(s);
            bgP    = makePaint("#0a0a0a", 0, Paint.Align.CENTER);
            doneP  = makePaint("#ffffff", 0, Paint.Align.CENTER);
            todayP = makePaint("#ff4f6d", 0, Paint.Align.CENTER);
            leftP  = makePaint("#2a2a2a", 0, Paint.Align.CENTER);
            clockP = makePaint("#ffffff", 0, Paint.Align.CENTER); clockP.setFakeBoldText(true);
            dateP  = makePaint("#666666", 0, Paint.Align.CENTER);
            statsP = makePaint("#ff4f6d", 0, Paint.Align.CENTER);
            barBgP = makePaint("#1e1e1e", 0, Paint.Align.CENTER);
            barP   = makePaint("#ff4f6d", 0, Paint.Align.CENTER);
        }

        Paint makePaint(String color, float size, Paint.Align align) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.parseColor(color));
            p.setTextSize(size);
            p.setTextAlign(align);
            return p;
        }

        @Override public void onVisibilityChanged(boolean v) {
            visible = v;
            if (v) handler.post(drawRunnable);
            else   handler.removeCallbacks(drawRunnable);
        }
        @Override public void onSurfaceDestroyed(SurfaceHolder h) { visible = false; handler.removeCallbacks(drawRunnable); }
        @Override public void onSurfaceChanged(SurfaceHolder h, int f, int w, int ht) { draw(); }

        void draw() {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) render(c);
            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }
            handler.removeCallbacks(drawRunnable);
            if (visible) {
                pulse += pulseDir;
                if (pulse > 1.15f) pulseDir = -0.02f;
                if (pulse < 1.00f) pulseDir =  0.02f;
                handler.postDelayed(drawRunnable, 50);
            }
        }

        void render(Canvas canvas) {
            float W = canvas.getWidth(), H = canvas.getHeight();
            canvas.drawRect(0, 0, W, H, bgP);

            SharedPreferences prefs = getSharedPreferences("LifeCalPrefs", MODE_PRIVATE);
            String mode = prefs.getString("mode", "year");
            long sMs = prefs.getLong("startMillis", 0);
            long eMs = prefs.getLong("endMillis", 0);

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);       today.set(Calendar.MILLISECOND, 0);

            int total, done;
            if (mode.equals("year")) {
                total = today.getActualMaximum(Calendar.DAY_OF_YEAR);
                done  = today.get(Calendar.DAY_OF_YEAR) - 1;
            } else if (sMs > 0) {
                Calendar s = Calendar.getInstance(TimeZone.getTimeZone("UTC")); s.setTimeInMillis(sMs);
                s.set(Calendar.HOUR_OF_DAY,0); s.set(Calendar.MINUTE,0); s.set(Calendar.SECOND,0); s.set(Calendar.MILLISECOND,0);
                Calendar e = Calendar.getInstance(TimeZone.getTimeZone("UTC")); e.setTimeInMillis(eMs > 0 ? eMs : sMs + 365L*86400000L);
                e.set(Calendar.HOUR_OF_DAY,0); e.set(Calendar.MINUTE,0); e.set(Calendar.SECOND,0); e.set(Calendar.MILLISECOND,0);
                total = (int)((e.getTimeInMillis()-s.getTimeInMillis())/86400000L)+1;
                done  = Math.max(0, Math.min((int)((today.getTimeInMillis()-s.getTimeInMillis())/86400000L), total));
            } else { total = 365; done = 0; }

            int pct = total > 0 ? (int)(done * 100.0 / total) : 0;
            float pad = W * 0.05f;

            // Clock
            Calendar now = Calendar.getInstance();
            String time = String.format("%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
            String[] mo = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
            String[] dy = {"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
            String dateStr = dy[now.get(Calendar.DAY_OF_WEEK)-1] + ", " + now.get(Calendar.DAY_OF_MONTH) + " " + mo[now.get(Calendar.MONTH)];

            float clockSize = W * 0.14f;
            clockP.setTextSize(clockSize);
            dateP.setTextSize(W * 0.035f);
            float clockY = H * 0.10f;
            canvas.drawText(time, W/2, clockY + clockSize, clockP);
            canvas.drawText(dateStr.toUpperCase(), W/2, clockY + clockSize + W*0.045f, dateP);

            // Dot grid
            float gridTop = H * 0.22f;
            float gridBot = H * 0.88f;
            float gridW   = W - pad * 2;
            float gridH   = gridBot - gridTop;
            int cols = 20;
            float dotSp = gridW / cols;
            float dotR  = dotSp * 0.35f;
            int rows = (int)Math.ceil((double)total / cols);
            float actualH = rows * dotSp;
            float startY  = gridTop + (gridH - actualH) / 2f;

            for (int i = 0; i < total; i++) {
                float cx = pad + (i % cols) * dotSp + dotSp/2f;
                float cy = startY + (i / cols) * dotSp + dotSp/2f;
                if (i == done) {
                    todayP.setShadowLayer(dotR*2, 0, 0, 0x88ff4f6d);
                    canvas.drawCircle(cx, cy, dotR * pulse, todayP);
                } else if (i < done) {
                    doneP.clearShadowLayer();
                    canvas.drawCircle(cx, cy, dotR, doneP);
                } else {
                    leftP.clearShadowLayer();
                    canvas.drawCircle(cx, cy, dotR, leftP);
                }
            }

            // Stats bar
            float statsY = gridBot + (H - gridBot) * 0.35f;
            statsP.setTextSize(W * 0.042f);
            canvas.drawText(done + "d done  ·  " + pct + "%  ·  " + (total-done) + "d left", W/2, statsY, statsP);

            // Progress bar
            float barY = gridBot + (H - gridBot) * 0.68f;
            float barH = H * 0.005f;
            canvas.drawRoundRect(new RectF(pad, barY, W-pad, barY+barH), barH/2, barH/2, barBgP);
            float fw = (W - pad*2) * pct / 100f;
            if (fw > 0) canvas.drawRoundRect(new RectF(pad, barY, pad+fw, barY+barH), barH/2, barH/2, barP);
        }
    }
}
