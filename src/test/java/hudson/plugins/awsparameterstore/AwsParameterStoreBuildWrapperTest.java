/**
  * MIT License
  *
  * Copyright (c) 2017 Rik Turnbull
  *
  * Permission is hereby granted, free of charge, to any person obtaining a copy
  * of this software and associated documentation files (the "Software"), to deal
  * in the Software without restriction, including without limitation the rights
  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  * copies of the Software, and to permit persons to whom the Software is
  * furnished to do so, subject to the following conditions:
  *
  * The above copyright notice and this permission notice shall be included in all
  * copies or substantial portions of the Software.
  *
  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  * SOFTWARE.
  */
package hudson.plugins.awsparameterstore;

import java.util.Arrays;
import java.util.Collection;

import jenkins.tasks.SimpleBuildWrapper;

import org.apache.commons.lang.StringUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mockito;
import org.powermock.api.easymock.PowerMock;
import org.powermock.api.easymock.annotation.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

/**
 * Run tests for {@link AwsParameterStoreService}.
 *
 * @author Rik Turnbull
 *
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(value = Parameterized.class)
@PrepareForTest({AwsParameterStoreBuildWrapper.class})
public class AwsParameterStoreBuildWrapperTest {

  private final static String CREDENTIALS_AWS_ADMIN = "aws-admin";
  private final static String CREDENTIALS_AWS_NO_DESCRIBE = "aws-nodescribe";

  private final static String REGION_NAME = "eu-west-1";

  @Parameter(0)
  public String path;
  @Parameter(1)
  public Boolean recursive;
  @Parameter(2)
  public String credentialsId;
  @Parameter(3)
  public String naming;
  @Parameter(4)
  public String namePrefixes;
  @Parameter(5)
  public Boolean exactNames;

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
      new Object[][] {
        {
          "/service/",
          false,
          CREDENTIALS_AWS_ADMIN,
          "basename",
          "",
          false
        },
        {
          null,
          true,
          CREDENTIALS_AWS_NO_DESCRIBE,
          "relative",
          "",
          false
        },
        {
          null,
          false,
          CREDENTIALS_AWS_NO_DESCRIBE,
          "",
          "name_prefix",
          false
        }
      }
    );
  }

  /**
   * Setups mock service classes.
   */
  @Before
  public void setUp() {
    mockAwsParameterStoreService();
  }

  /**
   * Test that the getters return values set in the constructor.
   */
  @Test
  public void testConstructor() {
    AwsParameterStoreBuildWrapper awsParameterStoreBuildWrapper = new AwsParameterStoreBuildWrapper(credentialsId, REGION_NAME, path, recursive, naming, namePrefixes, exactNames);
    Assert.assertEquals("credentialsId", credentialsId, awsParameterStoreBuildWrapper.getCredentialsId());
    Assert.assertEquals("regionName", REGION_NAME, awsParameterStoreBuildWrapper.getRegionName());
    Assert.assertEquals("path", path, awsParameterStoreBuildWrapper.getPath());
    Assert.assertEquals("recursive", recursive, awsParameterStoreBuildWrapper.getRecursive());
    Assert.assertEquals("naming", naming, awsParameterStoreBuildWrapper.getNaming());
    Assert.assertEquals("namePrefixes", namePrefixes, awsParameterStoreBuildWrapper.getNamePrefixes());
    Assert.assertEquals("exactNames", exactNames, awsParameterStoreBuildWrapper.getExactNames());
  }

  /**
   * Test that the getters return values set in the constructor.
   */
  @Test
  public void testSetters() {
    AwsParameterStoreBuildWrapper awsParameterStoreBuildWrapper = new AwsParameterStoreBuildWrapper();
    awsParameterStoreBuildWrapper.setCredentialsId(credentialsId);
    awsParameterStoreBuildWrapper.setRegionName(REGION_NAME);
    awsParameterStoreBuildWrapper.setPath(path);
    awsParameterStoreBuildWrapper.setRecursive(recursive);
    awsParameterStoreBuildWrapper.setNaming(naming);
    awsParameterStoreBuildWrapper.setNamePrefixes(namePrefixes);

    Assert.assertEquals("credentialsId", credentialsId, awsParameterStoreBuildWrapper.getCredentialsId());
    Assert.assertEquals("regionName", REGION_NAME, awsParameterStoreBuildWrapper.getRegionName());
    Assert.assertEquals("path", path, awsParameterStoreBuildWrapper.getPath());
    Assert.assertEquals("recursive", recursive, awsParameterStoreBuildWrapper.getRecursive());
    Assert.assertEquals("naming", naming, awsParameterStoreBuildWrapper.getNaming());
    Assert.assertEquals("namePrefixes", StringUtils.stripToNull(namePrefixes), awsParameterStoreBuildWrapper.getNamePrefixes());
    Assert.assertEquals("exactNames", exactNames, awsParameterStoreBuildWrapper.getExactNames());
  }

  /**
   * Test build wrapper setup.
   */
  @Test
  public void testSetup() {
    AwsParameterStoreBuildWrapper awsParameterStoreBuildWrapper = new AwsParameterStoreBuildWrapper(credentialsId, REGION_NAME, path, recursive, naming, namePrefixes, exactNames);
    try {
      awsParameterStoreBuildWrapper.setUp((SimpleBuildWrapper.Context)null, null, null, null, null, null);
    } catch(Exception e) {
      Assert.fail("Unexpected exception: " + e.getMessage());
    }
  }

  /**
   * Mocks the<code>AwsParameterStoreService</code> class to prevent lots of AWS interaction.
   */
  private void mockAwsParameterStoreService() {
    AwsParameterStoreService awsParameterStoreService = PowerMockito.mock(AwsParameterStoreService.class);
    try {
      PowerMockito.whenNew(AwsParameterStoreService.class).withAnyArguments().thenReturn(awsParameterStoreService);
    } catch(Exception e) {
      Assert.fail("Unexpected exception: " + e.getMessage());
    }
  }
}
