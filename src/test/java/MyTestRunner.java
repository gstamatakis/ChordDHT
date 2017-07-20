import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/**
 * The test runner used to run the {@link ChordNodeImplTest}.
 */
public class MyTestRunner {
    public static void main(String[] args) {
        Result result = JUnitCore.runClasses(ChordNodeImplTest.class);
        for (Failure failure : result.getFailures()) {
            System.out.println(failure.toString());
        }
    }
}
