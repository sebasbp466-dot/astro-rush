package com.example.spacegame;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

/**
 * ASTRO RUSH - todo el juego vive en esta vista (lógica + dibujo).
 *
 * Coordenadas "lógicas": el lado corto de la pantalla mide siempre 360 unidades,
 * así el juego se ve y se siente igual en cualquier teléfono.
 *
 * Dónde tocar para cambiar cosas:
 *  - Dificultad:       spawnRock() y el bloque "Nuevas rocas" de update()
 *  - Velocidad nave:   el factor 16f en update()
 *  - Cadencia disparo: shootTimer en update()
 *  - Textos:           drawMenu(), drawPause(), drawGameOver()
 *  - Colores:          las constantes C_... de arriba
 */
public class GameView extends View {

    // ---------- Estados ----------
    private static final int MENU = 0;
    private static final int PLAYING = 1;
    private static final int PAUSED = 2;
    private static final int GAME_OVER = 3;

    // ---------- Colores (ARGB) ----------
    private static final int C_BG = 0xFF070B1E;
    private static final int C_SHIP = 0xFF00E5FF;
    private static final int C_COCKPIT = 0xFFE0F7FA;
    private static final int C_FLAME = 0xFFFF9800;
    private static final int C_ROCK = 0xFF8D6E63;
    private static final int C_ROCK_EDGE = 0xFF3E2723;
    private static final int C_BULLET = 0xFFFFEB3B;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_SOFT = 0xB3FFFFFF;
    private static final int C_LIFE = 0xFFFF5252;
    private static final int C_TRIPLE = 0xFFFFC107;
    private static final int C_DIM = 0xB0000000;
    private static final int C_BLACK = 0xFF000000;

    // ---------- Objetos del juego ----------
    private static class Bullet {
        float x, y, vx, vy;
    }

    private static class Rock {
        float x, y, vx, vy, r, rot, rotSpeed, flash;
        int hp, maxHp;
        float[] shape; // radio relativo de cada vértice (da forma irregular)
    }

    /** kind 0 = vida extra, kind 1 = disparo triple */
    private static class Orb {
        float x, y, vy, t;
        int kind;
    }

    private static class Spark {
        float x, y, vx, vy, life, maxLife, size;
        int color;
    }

    private static class Star {
        float x, y, speed, size;
    }

    private final Random rnd = new Random();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final SharedPreferences prefs;

    private final ArrayList<Bullet> bullets = new ArrayList<>();
    private final ArrayList<Rock> rocks = new ArrayList<>();
    private final ArrayList<Orb> orbs = new ArrayList<>();
    private final ArrayList<Spark> sparks = new ArrayList<>();
    private final Star[] stars = new Star[70];

    private float scale = 1f; // píxeles por unidad lógica
    private float W = 360f;   // ancho lógico
    private float H = 640f;   // alto lógico

    private int state = MENU;
    private float stateTime = 0f;
    private long lastNanos = 0L;

    private float px, py;           // posición de la nave
    private float targetX, targetY; // hacia dónde la lleva el dedo
    private int lives, score, best;
    private boolean newRecord;
    private float invuln;           // segundos de invulnerabilidad
    private float shootTimer;
    private float tripleTimer;      // segundos que quedan de disparo triple
    private float time;             // segundos jugados en la partida
    private float spawnTimer;
    private float shake;            // temblor de pantalla (0..1)

    public GameView(Context context) {
        super(context);
        setKeepScreenOn(true);
        prefs = context.getSharedPreferences("astro_rush", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        for (int i = 0; i < stars.length; i++) {
            stars[i] = new Star();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w <= 0 || h <= 0) return;
        scale = Math.min(w, h) / 360f;
        W = w / scale;
        H = h / scale;
        for (Star s : stars) {
            s.x = rnd.nextFloat() * W;
            s.y = rnd.nextFloat() * H;
            s.speed = 15f + rnd.nextFloat() * 85f;
            s.size = 0.5f + s.speed / 70f;
        }
        px = W / 2f;
        py = H * 0.72f;
        targetX = px;
        targetY = py;
    }

    /** Lo llama la Activity cuando la app pierde el foco. */
    public void pauseGame() {
        if (state == PLAYING) goTo(PAUSED);
    }

    // ======================================================================
    //  Control
    // ======================================================================

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX() / scale;
        float y = e.getY() / scale;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (state == MENU || (state == GAME_OVER && stateTime > 0.7f)) {
                    startGame();
                } else if (state == PAUSED) {
                    goTo(PLAYING);
                }
                // sigue: el dedo recién puesto también mueve la nave
            case MotionEvent.ACTION_MOVE:
                if (state == PLAYING) {
                    targetX = x;
                    targetY = y - 70f; // la nave va por encima del dedo
                }
                break;
            default:
                break;
        }
        return true;
    }

    private void goTo(int newState) {
        state = newState;
        stateTime = 0f;
    }

    private void startGame() {
        bullets.clear();
        rocks.clear();
        orbs.clear();
        sparks.clear();
        score = 0;
        lives = 3;
        time = 0f;
        invuln = 1.5f;
        shootTimer = 0f;
        tripleTimer = 0f;
        spawnTimer = 0.8f;
        shake = 0f;
        newRecord = false;
        px = W / 2f;
        py = H * 0.72f;
        targetX = px;
        targetY = py;
        goTo(PLAYING);
    }

    private void gameOver() {
        if (score > best) {
            best = score;
            newRecord = true;
            prefs.edit().putInt("best", best).apply();
        }
        spawnSparks(px, py, 50, C_SHIP, 240f, 1.1f);
        spawnSparks(px, py, 25, C_FLAME, 200f, 0.9f);
        shake = 1f;
        bullets.clear();
        rocks.clear();
        orbs.clear();
        goTo(GAME_OVER);
    }

    private void hitPlayer() {
        lives--;
        invuln = 1.6f;
        tripleTimer = 0f;
        shake = 1f;
        spawnSparks(px, py, 24, C_SHIP, 170f, 0.7f);
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (lives <= 0) gameOver();
    }

    // ======================================================================
    //  Lógica (se llama una vez por fotograma)
    // ======================================================================

    private void update(float dt) {
        stateTime += dt;

        // Estrellas: siempre se mueven (más lento fuera de partida)
        float starMul = (state == PLAYING) ? 1f : 0.35f;
        for (Star s : stars) {
            s.y += s.speed * starMul * dt;
            if (s.y > H) {
                s.y = -2f;
                s.x = rnd.nextFloat() * W;
            }
        }

        // Chispas de explosión
        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark s = sparks.get(i);
            s.life -= dt;
            if (s.life <= 0f) {
                sparks.remove(i);
                continue;
            }
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            float drag = 1f - 1.5f * dt;
            s.vx *= drag;
            s.vy *= drag;
        }

        if (shake > 0f) shake = Math.max(0f, shake - 2.2f * dt);

        if (state != PLAYING) return;

        time += dt;
        if (invuln > 0f) invuln -= dt;
        if (tripleTimer > 0f) tripleTimer -= dt;

        // La nave sigue al dedo con suavidad
        float follow = Math.min(1f, 16f * dt);
        px += (targetX - px) * follow;
        py += (targetY - py) * follow;
        px = clamp(px, 22f, W - 22f);
        py = clamp(py, 90f, H - 40f);

        // Disparo automático
        shootTimer -= dt;
        if (shootTimer <= 0f) {
            if (tripleTimer > 0f) {
                shootTimer = 0.17f;
                fire(px, py - 24f, 0f);
                fire(px - 10f, py - 16f, -110f);
                fire(px + 10f, py - 16f, 110f);
            } else {
                shootTimer = 0.23f;
                fire(px, py - 24f, 0f);
            }
        }

        // Nuevas rocas: cada vez más seguidas
        spawnTimer -= dt;
        if (spawnTimer <= 0f) {
            spawnRock();
            float base = Math.max(0.30f, 0.95f - time * 0.009f);
            spawnTimer = base * (0.7f + rnd.nextFloat() * 0.6f);
        }

        updateBullets(dt);
        if (!updateRocks(dt)) return; // false = se acabó la partida
        updateOrbs(dt);
    }

    private void updateBullets(float dt) {
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            if (b.y < -20f || b.x < -20f || b.x > W + 20f) {
                bullets.remove(i);
            }
        }
    }

    /** Devuelve false si la partida terminó durante este fotograma. */
    private boolean updateRocks(float dt) {
        for (int i = rocks.size() - 1; i >= 0; i--) {
            Rock r = rocks.get(i);
            r.x += r.vx * dt;
            r.y += r.vy * dt;
            r.rot += r.rotSpeed * dt;
            if (r.flash > 0f) r.flash -= dt;

            // Rebotan en las paredes
            if (r.x < r.r) {
                r.x = r.r;
                r.vx = Math.abs(r.vx);
            } else if (r.x > W - r.r) {
                r.x = W - r.r;
                r.vx = -Math.abs(r.vx);
            }

            // Se salió por abajo
            if (r.y > H + r.r + 10f) {
                rocks.remove(i);
                continue;
            }

            // ¿Le dio alguna bala?
            boolean destroyed = false;
            for (int j = bullets.size() - 1; j >= 0; j--) {
                Bullet b = bullets.get(j);
                float dx = b.x - r.x;
                float dy = b.y - r.y;
                float rr = r.r + 4f;
                if (dx * dx + dy * dy < rr * rr) {
                    bullets.remove(j);
                    r.hp--;
                    r.flash = 0.08f;
                    spawnSparks(b.x, b.y, 3, C_BULLET, 70f, 0.25f);
                    if (r.hp <= 0) {
                        destroyed = true;
                        break;
                    }
                }
            }
            if (destroyed) {
                score += r.maxHp * 10;
                explode(r.x, r.y, r.r);
                maybeDropOrb(r.x, r.y);
                rocks.remove(i);
                continue;
            }

            // ¿Chocó con la nave?
            if (invuln <= 0f) {
                float dx = px - r.x;
                float dy = py - r.y;
                float rr = r.r * 0.85f + 12f;
                if (dx * dx + dy * dy < rr * rr) {
                    rocks.remove(i);
                    explode(r.x, r.y, r.r);
                    hitPlayer();
                    if (state != PLAYING) return false;
                }
            }
        }
        return true;
    }

    private void updateOrbs(float dt) {
        for (int i = orbs.size() - 1; i >= 0; i--) {
            Orb o = orbs.get(i);
            o.y += o.vy * dt;
            o.t += dt;
            if (o.y > H + 20f) {
                orbs.remove(i);
                continue;
            }
            float dx = px - o.x;
            float dy = py - o.y;
            if (dx * dx + dy * dy < 34f * 34f) {
                if (o.kind == 0) {
                    if (lives < 5) {
                        lives++;
                    } else {
                        score += 50;
                    }
                } else {
                    tripleTimer = 8f;
                }
                spawnSparks(o.x, o.y, 14, o.kind == 0 ? C_LIFE : C_TRIPLE, 120f, 0.5f);
                orbs.remove(i);
            }
        }
    }

    private void fire(float x, float y, float vx) {
        Bullet b = new Bullet();
        b.x = x;
        b.y = y;
        b.vx = vx;
        b.vy = -620f;
        bullets.add(b);
    }

    private void spawnRock() {
        Rock r = new Rock();
        float roll = rnd.nextFloat();
        if (roll < 0.20f) {
            r.r = 34f; // grande
            r.hp = 6;
        } else if (roll < 0.55f) {
            r.r = 24f; // mediana
            r.hp = 3;
        } else {
            r.r = 16f; // chica
            r.hp = 1;
        }
        r.maxHp = r.hp;
        float speedMul = 1f + Math.min(1.2f, time * 0.012f);
        float sizeMul = r.r > 30f ? 0.7f : 1f;
        r.x = r.r + rnd.nextFloat() * (W - 2f * r.r);
        r.y = -r.r - 4f;
        r.vx = (rnd.nextFloat() - 0.5f) * 50f;
        r.vy = (60f + rnd.nextFloat() * 70f) * speedMul * sizeMul;
        r.rot = rnd.nextFloat() * 360f;
        r.rotSpeed = (rnd.nextFloat() - 0.5f) * 120f;
        r.shape = new float[9];
        for (int i = 0; i < r.shape.length; i++) {
            r.shape[i] = 0.72f + rnd.nextFloat() * 0.28f;
        }
        rocks.add(r);
    }

    private void maybeDropOrb(float x, float y) {
        if (rnd.nextFloat() < 0.12f) {
            Orb o = new Orb();
            o.x = x;
            o.y = y;
            o.vy = 80f;
            o.kind = rnd.nextBoolean() ? 0 : 1;
            orbs.add(o);
        }
    }

    private void explode(float x, float y, float radius) {
        spawnSparks(x, y, (int) (8 + radius * 0.6f), C_ROCK, 130f, 0.55f);
        spawnSparks(x, y, 6, C_FLAME, 170f, 0.4f);
        shake = Math.max(shake, radius > 30f ? 0.35f : 0.12f);
    }

    private void spawnSparks(float x, float y, int n, int color, float speed, float life) {
        for (int i = 0; i < n; i++) {
            Spark s = new Spark();
            double a = rnd.nextDouble() * Math.PI * 2.0;
            float v = speed * (0.3f + rnd.nextFloat() * 0.7f);
            s.x = x;
            s.y = y;
            s.vx = (float) Math.cos(a) * v;
            s.vy = (float) Math.sin(a) * v;
            s.maxLife = life * (0.5f + rnd.nextFloat() * 0.5f);
            s.life = s.maxLife;
            s.size = 1.5f + rnd.nextFloat() * 2.5f;
            s.color = color;
            sparks.add(s);
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ======================================================================
    //  Dibujo
    // ======================================================================

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = (lastNanos == 0L) ? 0f : (now - lastNanos) / 1e9f;
        lastNanos = now;
        if (dt > 0.05f) dt = 0.05f; // evita saltos tras una pausa
        update(dt);

        c.drawColor(C_BG);

        // Escena (con temblor de pantalla)
        c.save();
        c.scale(scale, scale);
        if (shake > 0f) {
            c.translate((rnd.nextFloat() - 0.5f) * 12f * shake,
                    (rnd.nextFloat() - 0.5f) * 12f * shake);
        }
        drawStars(c);
        drawOrbs(c);
        drawRocks(c);
        drawBullets(c);
        drawSparks(c);
        if (state == PLAYING || state == PAUSED) {
            drawPlayer(c, px, py);
        } else if (state == MENU) {
            drawPlayer(c, W / 2f, H * 0.72f + (float) Math.sin(stateTime * 2.0) * 6f);
        }
        c.restore();

        // Interfaz (sin temblor)
        c.save();
        c.scale(scale, scale);
        if (state == PLAYING || state == PAUSED) drawHud(c);
        if (state == MENU) drawMenu(c);
        else if (state == PAUSED) drawPause(c);
        else if (state == GAME_OVER) drawGameOver(c);
        c.restore();

        postInvalidateOnAnimation(); // pide el siguiente fotograma
    }

    private void drawStars(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(C_TEXT);
        for (Star s : stars) {
            p.setAlpha((int) Math.min(255f, 50f + s.speed * 2f));
            c.drawCircle(s.x, s.y, s.size, p);
        }
        p.setAlpha(255);
    }

    private void drawRocks(Canvas c) {
        for (Rock r : rocks) {
            c.save();
            c.translate(r.x, r.y);
            c.rotate(r.rot);
            path.reset();
            int n = r.shape.length;
            for (int i = 0; i < n; i++) {
                double a = i * (2.0 * Math.PI / n);
                float qx = (float) Math.cos(a) * r.r * r.shape[i];
                float qy = (float) Math.sin(a) * r.r * r.shape[i];
                if (i == 0) {
                    path.moveTo(qx, qy);
                } else {
                    path.lineTo(qx, qy);
                }
            }
            path.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(r.flash > 0f ? C_TEXT : C_ROCK);
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setColor(C_ROCK_EDGE);
            c.drawPath(path, p);
            c.restore();
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawBullets(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(C_BULLET);
        for (Bullet b : bullets) {
            c.drawRoundRect(b.x - 2f, b.y - 8f, b.x + 2f, b.y + 8f, 2f, 2f, p);
        }
    }

    private void drawSparks(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        for (Spark s : sparks) {
            p.setColor(s.color);
            p.setAlpha((int) (255f * Math.max(0f, s.life / s.maxLife)));
            c.drawCircle(s.x, s.y, s.size, p);
        }
        p.setAlpha(255);
    }

    private void drawOrbs(Canvas c) {
        for (Orb o : orbs) {
            int col = (o.kind == 0) ? C_LIFE : C_TRIPLE;
            float pulse = 1f + 0.15f * (float) Math.sin(o.t * 8f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(col);
            p.setAlpha(70);
            c.drawCircle(o.x, o.y, 17f * pulse, p);
            p.setColor(col);
            c.drawCircle(o.x, o.y, 10f, p);
            drawText(c, o.kind == 0 ? "+" : "3", o.x, o.y + 5f, 14f, C_BLACK, Paint.Align.CENTER);
        }
    }

    private void drawShip(Canvas c, float x, float y, float s, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        path.reset();
        path.moveTo(x, y - 24f * s);
        path.lineTo(x + 18f * s, y + 16f * s);
        path.lineTo(x + 7f * s, y + 9f * s);
        path.lineTo(x, y + 14f * s);
        path.lineTo(x - 7f * s, y + 9f * s);
        path.lineTo(x - 18f * s, y + 16f * s);
        path.close();
        c.drawPath(path, p);
        p.setColor(C_COCKPIT);
        c.drawCircle(x, y - 3f * s, 5f * s, p);
    }

    private void drawPlayer(Canvas c, float x, float y) {
        // Parpadea mientras es invulnerable
        if (invuln > 0f && ((int) (invuln * 12f)) % 2 == 0) return;
        // Llama del motor
        float flame = 10f + rnd.nextFloat() * 8f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(C_FLAME);
        path.reset();
        path.moveTo(x - 6f, y + 12f);
        path.lineTo(x, y + 12f + flame);
        path.lineTo(x + 6f, y + 12f);
        path.close();
        c.drawPath(path, p);
        drawShip(c, x, y, 1f, C_SHIP);
    }

    private void drawHud(Canvas c) {
        float top = 44f; // deja espacio para la barra de estado / cámara
        drawText(c, "PUNTOS " + score, 14f, top, 18f, C_TEXT, Paint.Align.LEFT);
        drawText(c, "RÉCORD " + best, 14f, top + 18f, 12f, C_SOFT, Paint.Align.LEFT);
        if (tripleTimer > 0f) {
            drawText(c, "TRIPLE " + (int) Math.ceil(tripleTimer), 14f, top + 38f, 14f,
                    C_TRIPLE, Paint.Align.LEFT);
        }
        for (int i = 0; i < lives; i++) {
            drawShip(c, W - 22f - i * 26f, top - 6f, 0.5f, C_SHIP);
        }
    }

    private void drawMenu(Canvas c) {
        drawText(c, "ASTRO RUSH", W / 2f, H * 0.28f, 38f, C_SHIP, Paint.Align.CENTER);
        drawText(c, "Arrastra el dedo para mover la nave", W / 2f, H * 0.28f + 40f, 13f,
                C_TEXT, Paint.Align.CENTER);
        drawText(c, "Dispara sola: esquiva y destruye", W / 2f, H * 0.28f + 60f, 13f,
                C_SOFT, Paint.Align.CENTER);
        if (((int) (stateTime * 2f)) % 2 == 0) {
            drawText(c, "TOCA PARA JUGAR", W / 2f, H * 0.48f, 20f, C_BULLET, Paint.Align.CENTER);
        }
        if (best > 0) {
            drawText(c, "Récord: " + best, W / 2f, H * 0.48f + 34f, 15f, C_TEXT,
                    Paint.Align.CENTER);
        }
    }

    private void drawPause(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(C_DIM);
        c.drawRect(0f, 0f, W, H, p);
        drawText(c, "PAUSA", W / 2f, H * 0.42f, 36f, C_TEXT, Paint.Align.CENTER);
        drawText(c, "Toca para continuar", W / 2f, H * 0.42f + 36f, 16f, C_SOFT,
                Paint.Align.CENTER);
    }

    private void drawGameOver(Canvas c) {
        float y = H * 0.34f;
        drawText(c, "GAME OVER", W / 2f, y, 36f, C_LIFE, Paint.Align.CENTER);
        drawText(c, "Puntos: " + score, W / 2f, y + 44f, 22f, C_TEXT, Paint.Align.CENTER);
        if (newRecord) {
            drawText(c, "¡NUEVO RÉCORD!", W / 2f, y + 78f, 20f, C_BULLET, Paint.Align.CENTER);
        } else {
            drawText(c, "Récord: " + best, W / 2f, y + 78f, 16f, C_SOFT, Paint.Align.CENTER);
        }
        if (stateTime > 0.7f && ((int) (stateTime * 2f)) % 2 == 0) {
            drawText(c, "TOCA PARA REINTENTAR", W / 2f, y + 124f, 17f, C_TEXT,
                    Paint.Align.CENTER);
        }
    }

    private void drawText(Canvas c, String s, float x, float y, float size, int color,
                          Paint.Align align) {
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(size);
        p.setColor(color);
        p.setTextAlign(align);
        c.drawText(s, x, y, p);
    }
}
