/**
  * MIT License
  *
  * Copyright (c) 2018 Rik Turnbull
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClient;
import com.amazonaws.services.simplesystemsmanagement.model.DescribeParametersRequest;
import com.amazonaws.services.simplesystemsmanagement.model.DescribeParametersResult;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterResult;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathResult;
import com.amazonaws.services.simplesystemsmanagement.model.Parameter;
import com.amazonaws.services.simplesystemsmanagement.model.ParameterMetadata;
import com.amazonaws.services.simplesystemsmanagement.model.ParameterStringFilter;

import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsHelper;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;

import hudson.ProxyConfiguration;

import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildWrapper;

import org.apache.commons.lang.StringUtils;

/**
 * AWS Parameter Store client.
 *
 * @author Rik Turnbull
 */
public class AwsParameterStoreService {
  public static final String DEFAULT_REGION = "us-east-1";
  public static final String NAMING_BASENAME = "basename";
  public static final String NAMING_RELATIVE = "relative";
  public static final String NAMING_ABSOLUTE = "absolute";

  private static final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
  private static final String AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
  private static final String AWS_SESSION_TOKEN = "AWS_SESSION_TOKEN";

  private static final Logger LOGGER = Logger.getLogger(AwsParameterStoreService.class.getName());

  private AWSSimpleSystemsManagement client;

  private String credentialsId;
  private String regionName;

  /**
   * Creates a new {@link AwsParameterStoreService}.
   *
   * @param credentialsId  AWS credentials identifier
   * @param regionName     AWS region name
   */
  public AwsParameterStoreService(String credentialsId, String regionName) {
    this.credentialsId = credentialsId;
    this.regionName = StringUtils.defaultString(regionName, DEFAULT_REGION);
  }

  /**
   * Returns an {@link AWSSimpleSystemsManagement}.
   * @param env execution environment variables
   *
   * @return {@link AWSSimpleSystemsManagement} singleton using the <code>credentialsId</code>
   * and <code>regionName</code>
   */
  private synchronized AWSSimpleSystemsManagement getAWSSimpleSystemsManagement(Map<String,String> env) {
    if(client == null) {
      ClientConfiguration clientConfiguration = new ClientConfiguration();
      Jenkins jenkins = Jenkins.getInstance();
      if(jenkins != null) {
        ProxyConfiguration proxy = jenkins.proxy;
        if(proxy != null) {
          clientConfiguration.setProxyHost(proxy.name);
          clientConfiguration.setProxyPort(proxy.port);
          clientConfiguration.setProxyUsername(proxy.getUserName());
          clientConfiguration.setProxyPassword(proxy.getPassword());
        }
      }

      // try and use either credentials provider from Jenkins or environment variables
      // otherwise fallback to default AWS credential chain from Jenkins master
      AWSCredentialsProvider credentialsProvider = getAWSCredentialsProvider(env, credentialsId);
      if(credentialsProvider == null) {
        client = AWSSimpleSystemsManagementClient.
                   builder().
                   withClientConfiguration(clientConfiguration).
                   withRegion(regionName).
                   build();
      } else {
        client = AWSSimpleSystemsManagementClient.
                   builder().
                   withCredentials(credentialsProvider).
                   withClientConfiguration(clientConfiguration).
                   withRegion(regionName).
                   build();
      }
    }
    return client;
  }

  /**
   * Gets AWS credentials provider. If a Jenkins credentialsId is supplied, use
   * inbuilt Jenkins provider, if AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY are
   * set then use static credentials provider, otherwise return none.
   *
   * @param env execution environment variables
   * @param credentialsId Jenkins credentials identifier
   * @return AWS credentials provider for <code>credentialsId</code> or from
   * environment
   */
  private AWSCredentialsProvider getAWSCredentialsProvider(Map<String,String> env, String credentialsId) {
    AWSCredentialsProvider credentialsProvider = null;

    if(StringUtils.isEmpty(credentialsId)) {
      if(env.containsKey(AWS_ACCESS_KEY_ID) && env.containsKey(AWS_SECRET_ACCESS_KEY)) {
        if(env.containsKey(AWS_SESSION_TOKEN)) {
          credentialsProvider = new AWSStaticCredentialsProvider(
                                  new BasicSessionCredentials(
                                    env.get(AWS_ACCESS_KEY_ID),
                                    env.get(AWS_SECRET_ACCESS_KEY),
                                    env.get(AWS_SESSION_TOKEN)
                                  )
                                );
        } else {
          credentialsProvider = new AWSStaticCredentialsProvider(
                                  new BasicAWSCredentials(
                                    env.get(AWS_ACCESS_KEY_ID),
                                    env.get(AWS_SECRET_ACCESS_KEY)
                                  )
                                );
        }
      }
    } else {
      credentialsProvider = AWSCredentialsHelper.getCredentials(credentialsId, Jenkins.getActiveInstance());
    }

    return credentialsProvider;
  }

  /**
   * Adds environment variables to <code>context</code>.
   *
   * @param context       SimpleBuildWrapper context
   * @param env           execution environment variables
   * @param path          hierarchy for the parameter
   * @param recursive     fetch all parameters within a hierarchy
   * @param naming        environment variable naming: basename, relative, absolute
   * @param namePrefixes  filter parameters by Name with beginsWith filter
   * @param exactNames    Whether name prefixes should be treated as exact parameter names
   */
  public void buildEnvVars(SimpleBuildWrapper.Context context, Map<String,String> env, String path, Boolean recursive, String naming, String namePrefixes, Boolean exactNames)  {
    if(StringUtils.isEmpty(path)) {
      buildEnvVarsWithParameters(context, env, namePrefixes, exactNames, naming);
    } else {
      buildEnvVarsWithParametersByPath(context, env, path, recursive, naming);
    }
  }

  /**
   * Adds environment variables to <code>context</code> using <code>describeParameters</code>.
   *
   * @param context       SimpleBuildWrapper context
   * @param env           execution environment variables
   * @param namePrefixes  filter parameters by Name with beginsWith filter
   * @param exactNames    Whether name prefixes should be treated as exact parameter names
   * @param naming        environment variable naming: basename, relative, absolute
   */
  private void buildEnvVarsWithParameters(SimpleBuildWrapper.Context context, Map<String,String> env, String namePrefixes, Boolean exactNames, String naming) {
    final AWSSimpleSystemsManagement client = getAWSSimpleSystemsManagement(env);
    List<String> names;

    if (exactNames) {
      LOGGER.log(Level.INFO, "Fetching parameters: \"" + namePrefixes + "\"");

      names = Arrays.asList(namePrefixes.split(","));
    } else {
      LOGGER.log(Level.INFO, "Fetching parameters starting with prefix: \"" + namePrefixes + "\"");

      names = getParameterNames(env, namePrefixes);
    }

    final GetParameterRequest getParameterRequest = new GetParameterRequest().withWithDecryption(true);
    for(String name : names) {
      getParameterRequest.setName(name);

      try {
        String envVarName = toEnvironmentVariable(name, getParameterPath(name), naming);

        String value = client.
                        getParameter(getParameterRequest).
                        getParameter().
                        getValue();

        context.env(envVarName, value);
      } catch(Exception e) {
        LOGGER.log(Level.WARNING, "Cannot fetch parameter: \"" + name + "\"", e);
      }
    }
  }

  /**
   * Fetches parameter names matching the prefixes.
   *
   * @param env           execution environment variables
   * @param namePrefixes  filter parameters by Name with beginsWith filter
   */
  private List<String> getParameterNames(Map<String,String> env, String namePrefixes) {
    final AWSSimpleSystemsManagement client = getAWSSimpleSystemsManagement(env);
    final List<String> names = new ArrayList<String>();

    try {
      DescribeParametersRequest describeParametersRequest = new DescribeParametersRequest().withMaxResults(1);
      if (!StringUtils.isEmpty(namePrefixes)) {
        describeParametersRequest = describeParametersRequest.withParameterFilters(
          new ParameterStringFilter().
            withKey("Name").
            withOption("BeginsWith").
            withValues(namePrefixes.split(","))
        );
      }

      do {
        final DescribeParametersResult describeParametersResult = client.describeParameters(describeParametersRequest);
        for(ParameterMetadata metadata : describeParametersResult.getParameters()) {
          names.add(metadata.getName());
        }
        describeParametersRequest.setNextToken(describeParametersResult.getNextToken());
      } while(describeParametersRequest.getNextToken() != null);
    } catch(Exception e) {
      LOGGER.log(Level.WARNING, "Cannot fetch parameters: " + e.getMessage(), e);
    }

    return names;
  }

  /**
   * Adds environment variables to <code>context</code> using <code>getParametersByPath</code>.
   *
   * @param context       SimpleBuildWrapper context
   * @param env           execution environment variables
   * @param path          hierarchy for the parameter
   * @param recursive     fetch all parameters within a hierarchy
   * @param naming        environment variable naming: basename, relative, absolute
   */
  public void buildEnvVarsWithParametersByPath(SimpleBuildWrapper.Context context, Map<String,String> env, String path, Boolean recursive, String naming)  {
    final AWSSimpleSystemsManagement client = getAWSSimpleSystemsManagement(env);

    LOGGER.log(Level.INFO, "Fetching all parameters by path: \"" + path + "\"");

    try {
      final GetParametersByPathRequest getParametersByPathRequest =
        new GetParametersByPathRequest().
              withPath(path).
              withRecursive(recursive).
              withWithDecryption(true);

      do {
        final GetParametersByPathResult getParametersByPathResult = client.getParametersByPath(getParametersByPathRequest);
        for(Parameter parameter : getParametersByPathResult.getParameters()) {
          try {
            String name = toEnvironmentVariable(parameter.getName(), path, naming);
            String value = parameter.getValue();

            context.env(name, value);
          } catch(Exception e) {
            LOGGER.log(Level.WARNING, "Cannot add parameter to environment: " + e.getMessage(), e);
          }
        }
        getParametersByPathRequest.setNextToken(getParametersByPathResult.getNextToken());
      } while(getParametersByPathRequest.getNextToken() != null);
    } catch(Exception e) {
      LOGGER.log(Level.WARNING, "Cannot fetch parameters by path: " + e.getMessage(), e);
    }
  }

  /**
   * Converts <code>name</code> to uppercase. All non alphanumeric characters are converted to underscores.
   * If <code>naming</code> is <code>basename</code> then the environment variable name is anything after the
   * last '/' in the parameter name, if it is <code>relative</code> then the environment variable name is
   * anything after the <code>path</code>, otherwise the full path is used.
   *
   * @param name    parameter name
   * @param path    hierarchy for the parameter
   * @param naming  environment variable naming: basename, relative, absolute
   */
  private String toEnvironmentVariable(String name, String path, String naming) {
    StringBuffer environmentVariable = new StringBuffer();
    int start = 0;
    if(path != null) {
      if(NAMING_RELATIVE.equals(naming)) {
        if(name.length() > path.length()) {
          start = path.length();
        }
      } else if(NAMING_ABSOLUTE.equals(naming)) {
        start = 1;
      } else {
        start = name.lastIndexOf('/')+1;
      }
    }
    if(name.charAt(start) == '/') {
      start++;
    }
    for(int i = start; i < name.length(); i++) {
      char c = name.charAt(i);
      if(Character.isLetter(c)) {
        environmentVariable.append(Character.toUpperCase(c));
      } else if(Character.isDigit(c)) {
        environmentVariable.append(c);
      } else {
        environmentVariable.append('_');
      }
    }
    return environmentVariable.toString();
  }

  private String getParameterPath(String name) {
    if (name.indexOf('/') == -1) {
      return null;
    }

    return name.substring(0, name.lastIndexOf('/'));
  }
}
