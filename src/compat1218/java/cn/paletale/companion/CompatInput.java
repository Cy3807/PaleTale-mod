package cn.paletale.companion;

/** 1.21.8 passes raw ints to keyPressed / mouseClicked; the translated sources wrap them in these so the shared code reads the same. */
final class CompatInput {
    record Key(int key) {}
    record Click(double x, double y, int button) {}
    private CompatInput() {}
}
