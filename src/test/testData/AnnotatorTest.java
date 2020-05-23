public class AnnotatorTest {
    public static void delay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            <weak_warning>e</weak_warning>.printStackTrace();
        }
    }
}
