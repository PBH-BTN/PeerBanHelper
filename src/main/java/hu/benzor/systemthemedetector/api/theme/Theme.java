package hu.benzor.systemthemedetector.api.theme;

public sealed interface Theme permits Theme.Appearance, Theme.AccentColor, Theme.Font {

    enum Appearance implements Theme {
        NO_PREFERENCE,
        DARK,
        LIGHT
    }

    record AccentColor(int red, int green, int blue) implements Theme {

        public AccentColor {
            verifyColorNumber(red);
            verifyColorNumber(blue);
            verifyColorNumber(green);
        }

        public static AccentColor fromArray(int[] rgbNumbers) {
            if (rgbNumbers.length != 3) {
                throw new IllegalArgumentException("Color array must have length 3.");
            }
            return new AccentColor(rgbNumbers[0], rgbNumbers[1], rgbNumbers[2]);
        }

        private static void verifyColorNumber(int input) {
            if (input < 0 || input > 255) {
                throw new IllegalArgumentException("Color number must be between 0 and 255 (both inclusive).");
            }
        }
    }

    record Font(String name, double size) implements Theme {
        public Font {
            if (name.isBlank()) {
                throw new IllegalArgumentException("Name must not be blank.");
            }
            if (size <= 0) {
                throw new IllegalArgumentException("Font size must be positive.");
            }
        }
    }
}
