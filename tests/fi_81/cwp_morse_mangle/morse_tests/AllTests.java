package fi_81.cwp_morse_mangle.morse_tests;

import junit.framework.Test;
import junit.framework.TestSuite;
import android.test.suitebuilder.TestSuiteBuilder;

/* As in http://marakana.com/s/android_junit_test_example_tutorial,1038/index.html */
public class AllTests extends TestSuite {
	public static Test suite() {
		return new TestSuiteBuilder(AllTests.class)
				.includeAllPackagesUnderHere().build();
	}
}
