package com.example.mymod.client.fx;

import static com.example.mymod.client.fx.MeshDraw.join;
import static com.example.mymod.client.fx.MeshDraw.ogive;

import com.example.mymod.fx.Fx;

final class Models {
	private static final int SEG = 28;
	private static final int BLACK = 0xFF1C1D20;
	private static final int DARK = 0xFF34373C;
	private static final int STEEL = 0xFF9097A0;
	private static final int CHROME = 0xFFC4CAD2;
	private static final int RED = 0xFFC42A2E;
	private static final int YELLOW = 0xFFE6B422;
	private static final int NOZZLE = 0xFF2B2622;
	private static final int NOZZLE_IN = 0xFF4A2A18;

	private Models() {
	}

	static void draw(MeshDraw m, int model, int paint) {
		switch (model) {
			case Fx.MODEL_ICBM -> icbm(m, paint);
			case Fx.MODEL_CRUISE -> cruise(m, paint);
			case Fx.MODEL_BOMB -> bomb(m, paint);
			case Fx.MODEL_WARHEAD -> warhead(m, paint);
			case Fx.MODEL_ROCKET -> rocket(m, paint);
			case Fx.MODEL_DRILL -> drill(m, paint);
			case Fx.MODEL_ROD -> rod(m);
			default -> rocket(m, paint);
		}
	}

	private static void nozzle(MeshDraw m, double yTop, double yBottom, double rTop, double rBottom) {
		double h = yTop - yBottom;
		m.lathe(new double[][] {
				{yBottom, rBottom}, {yBottom + h * 0.25, rBottom * 0.92}, {yBottom + h * 0.6, rTop * 1.15}, {yTop, rTop}},
				SEG, NOZZLE);
		m.latheInward(new double[][] {{yBottom + h * 0.02, rBottom * 0.94}, {yTop - h * 0.1, rTop * 0.7}}, SEG, NOZZLE_IN);
	}

	static void icbm(MeshDraw m, int paint) {
		double r = 0.058;
		nozzle(m, -0.5, -0.565, 0.034, 0.05);
		m.lathe(new double[][] {{-0.5, r * 0.96}, {-0.49, r}, {-0.1, r}}, SEG, DARK, paint);
		m.lathe(new double[][] {{-0.1, r * 1.03}, {-0.085, r * 1.03}}, SEG, BLACK);
		m.lathe(new double[][] {{-0.085, r * 0.97}, {0.2, r * 0.97}}, SEG, paint);
		m.lathe(new double[][] {{0.2, r * 1.0}, {0.215, r * 1.0}}, SEG, BLACK);

		m.lathe(join(new double[][] {{0.215, r * 0.93}, {0.3, r * 0.93}}, ogive(0.3, 0.5, r * 0.93, 12, 0.62)), SEG,
				paint, RED);
		m.cap(-0.1, r * 0.97, SEG, BLACK, false);
		for (int i = 0; i < 4; i++) {
			m.fin(Math.PI / 2 * i + Math.PI / 4, -0.49, -0.34, r, 0.075, 0.03, 0.009, BLACK);
		}

		m.box(r * 0.95, -0.42, -0.006, r * 1.12, 0.18, 0.006, MeshDraw.shade(paint, 0.8f));
	}

	static void cruise(MeshDraw m, int paint) {
		double r = 0.05;
		nozzle(m, -0.47, -0.51, 0.026, 0.032);
		m.lathe(join(new double[][] {{-0.47, r * 0.7}, {-0.4, r}, {0.28, r}}, ogive(0.28, 0.5, r, 12, 0.7)), SEG,
				MeshDraw.shade(paint, 0.9f), paint, paint, DARK);

		m.fin(0, -0.04, 0.16, r * 0.9, 0.34, 0.09, 0.012, MeshDraw.shade(paint, 0.85f));
		m.fin(Math.PI, -0.04, 0.16, r * 0.9, 0.34, 0.09, 0.012, MeshDraw.shade(paint, 0.85f));

		for (int i = 0; i < 3; i++) {
			m.fin(Math.PI / 2 + i * Math.PI * 2 / 3, -0.46, -0.33, r * 0.9, 0.085, 0.04, 0.008, DARK);
		}

		m.box(-0.025, -0.3, -r - 0.03, 0.025, -0.08, -r + 0.01, DARK);
		m.box(-0.02, -0.3, -r - 0.028, 0.02, -0.29, -r + 0.008, BLACK);
	}

	static void bomb(MeshDraw m, int paint) {
		m.lathe(new double[][] {
				{-0.5, 0.0}, {-0.5, 0.03}, {-0.44, 0.05}, {-0.33, 0.095}, {-0.2, 0.128}, {-0.05, 0.14},
				{0.12, 0.14}, {0.26, 0.128}, {0.36, 0.1}, {0.43, 0.068}, {0.475, 0.035}, {0.5, 0.0}},
				SEG, DARK, DARK, paint);
		m.lathe(new double[][] {{0.13, 0.1425}, {0.155, 0.1425}}, SEG, YELLOW);
		m.lathe(new double[][] {{0.2, 0.137}, {0.215, 0.135}}, SEG, YELLOW);

		for (int i = 0; i < 4; i++) {
			m.fin(Math.PI / 2 * i + Math.PI / 4, -0.5, -0.3, 0.04, 0.11, 0.0, 0.01, DARK);
		}
		m.lathe(new double[][] {{-0.5, 0.152}, {-0.44, 0.152}}, SEG, DARK);
		m.lathe(new double[][] {{-0.44, 0.143}, {-0.5, 0.143}}, SEG, BLACK);

		m.box(-0.012, 0.0, 0.135, 0.012, 0.05, 0.16, STEEL);
	}

	static void warhead(MeshDraw m, int paint) {
		m.cap(-0.32, 0.165, SEG, BLACK, false);
		m.lathe(new double[][] {{-0.32, 0.165}, {-0.26, 0.17}}, SEG, DARK);
		m.lathe(join(new double[][] {{-0.26, 0.17}, {0.3, 0.06}}, ogive(0.3, 0.5, 0.06, 8, 0.5)), SEG, paint);
	}

	static void rocket(MeshDraw m, int paint) {
		double r = 0.04;
		nozzle(m, -0.5, -0.54, 0.022, 0.032);
		m.lathe(new double[][] {{-0.5, r}, {0.18, r}}, SEG, paint);
		m.lathe(new double[][] {{0.18, r * 1.03}, {0.24, r * 1.03}}, SEG, RED);
		m.lathe(join(new double[][] {{0.24, r}, {0.3, r}}, ogive(0.3, 0.5, r, 10, 0.65)), SEG, paint, CHROME);
		for (int i = 0; i < 4; i++) {
			m.fin(Math.PI / 2 * i, -0.5, -0.3, r, 0.075, 0.045, 0.008, RED);
		}
	}

	static void drill(MeshDraw m, int paint) {
		double r = 0.08;
		nozzle(m, -0.47, -0.52, 0.045, 0.06);
		for (int i = 0; i < 6; i++) {
			double y0 = -0.47 + i * 0.105;
			m.lathe(new double[][] {{y0, r}, {y0 + 0.105, r}}, SEG, i % 2 == 0 ? paint : BLACK);
		}
		m.lathe(new double[][] {{0.16, r}, {0.2, r * 1.05}, {0.5, 0.0}}, SEG, STEEL, CHROME);
		for (int i = 0; i < 4; i++) {
			m.fin(i * Math.PI / 2 + 0.4, 0.2, 0.44, 0.02, 0.06, 0.12, 0.01, MeshDraw.shade(CHROME, 1.05f));
		}
	}

	static void rod(MeshDraw m) {
		m.cap(-0.5, 0.03, SEG, DARK, false);
		m.lathe(join(new double[][] {{-0.5, 0.03}, {0.4, 0.03}}, ogive(0.4, 0.5, 0.03, 6, 0.6)), SEG, 0xFF9AA0A8, CHROME);
	}

	static void satellite(MeshDraw m) {
		m.box(-0.12, -0.15, -0.12, 0.12, 0.15, 0.12, 0xFFD4A63A);
		m.box(-0.125, -0.16, -0.125, 0.125, -0.13, 0.125, DARK);
		m.box(-0.125, 0.13, -0.125, 0.125, 0.16, 0.125, DARK);

		for (int side = -1; side <= 1; side += 2) {
			double x0 = side < 0 ? -0.64 : 0.16;
			double x1 = side < 0 ? -0.16 : 0.64;
			m.box(x0, -0.012, -0.1, x1, 0.012, 0.1, DARK);
			for (int k = 0; k < 4; k++) {
				double cx0 = x0 + 0.012 + k * (x1 - x0 - 0.024) / 4;
				double cx1 = cx0 + (x1 - x0 - 0.024) / 4 - 0.008;
				m.box(cx0, -0.014, -0.09, cx1, 0.014, 0.09, 0xFF1D3F8C);
			}
		}
		m.box(-0.16, -0.006, -0.012, 0.16, 0.006, 0.012, STEEL);

		m.lathe(new double[][] {{0.16, 0.012}, {0.175, 0.05}, {0.19, 0.08}, {0.21, 0.11}}, SEG, 0xFFE8E8EC);
		m.tube(0.16, 0.3, 0.006, 0.004, 8, STEEL);
		m.lathe(new double[][] {{0.3, 0.0}, {0.305, 0.012}, {0.315, 0.0}}, 8, RED);
	}
}
