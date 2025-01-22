// Copyright 2013 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.termux.x11.input;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * This class stores UI configuration that will be used when rendering the remote desktop.
 */
public class RenderData {
    public PointF scale = new PointF();

    public int screenWidth;
    public int screenHeight;
    public int imageWidth;
    public int imageHeight;

    private final PointF mCursorPosition = new PointF();

    private float cpuTemperature = 0.0f; // Переменная для хранения температуры процессора
    private final Paint temperaturePaint = new Paint(); // Кисть для текста температуры
    private final Handler handler = new Handler(Looper.getMainLooper());

    public RenderData() {
        temperaturePaint.setTextSize(50); // Размер текста
        updateCpuTemperature(); // Инициализация обновления температуры
    }

    public PointF getCursorPosition() {
        return new PointF(mCursorPosition.x, mCursorPosition.y);
    }

    public boolean setCursorPosition(float newX, float newY) {
        boolean cursorMoved = false;
        if (newX != mCursorPosition.x) {
            mCursorPosition.x = newX;
            cursorMoved = true;
        }
        if (newY != mCursorPosition.y) {
            mCursorPosition.y = newY;
            cursorMoved = true;
        }

        return cursorMoved;
    }

    /**
     * Обновление температуры процессора.
     */
    private void updateCpuTemperature() {
        handler.postDelayed(() -> {
            cpuTemperature = getCpuTemperature();
            updateCpuTemperature();
        }, 1000); // Обновление каждые 1 секунду
    }

    /**
     * Получение температуры процессора.
     *
     * @return Температура процессора в градусах Цельсия.
     */
    private float getCpuTemperature() {
        String tempPath = "/sys/class/thermal/thermal_zone0/temp";
        try (BufferedReader reader = new BufferedReader(new FileReader(tempPath))) {
            String line = reader.readLine();
            if (line != null) {
                return Float.parseFloat(line) / 1000.0f; // Конвертация в градусы Цельсия
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0.0f; // Возврат 0.0 в случае ошибки
    }

    /**
     * Рендеринг температуры процессора на экране.
     *
     * @param canvas Канва для рендеринга.
     */
    public void renderCpuTemperature(Canvas canvas) {
        String tempText = "CPU Temp: " + cpuTemperature + "°C";

        // Установка цвета текста в зависимости от температуры
        if (cpuTemperature > 70) {
            temperaturePaint.setColor(Color.RED);
        } else if (cpuTemperature > 50) {
            temperaturePaint.setColor(Color.YELLOW);
        } else if (cpuTemperature > 40) {
            temperaturePaint.setColor(Color.GREEN);
        } else {
            temperaturePaint.setColor(Color.CYAN);
        }

        // Рисование текста в верхнем левом углу
        canvas.drawText(tempText, 10, 60, temperaturePaint);
    }
}
