package com.twilio.jenkins;

import hudson.Extension;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.twilio.sdk.TwilioRestClient;
import com.twilio.sdk.TwilioRestException;
import com.twilio.sdk.resource.factory.CallFactory;
import com.twilio.sdk.resource.factory.SmsFactory;
import com.twilio.sdk.resource.instance.Account;

/**
 * A {@link TwilioNotifier} is a {@link Notifier} that uses the Rest API of communications service provider
 * {@linkplain "http://www.twilio.com/} to send text messages and make calls with the build status of individual
 * builds. cgabge
 * 
 * This notifier was inspired in features and design by the Twitter Notifier plugin.
 * 
 * @author Christer Fahlgren (christer@twilio.com)
 */
public class TwilioNotifier extends Notifier {  

    /**
     * The message to send/read to the recipient.
     */
    private final String message;

    /**
     * The list of phone numbers, comma separated of people who should receive the communications.
     */
    private final String toList;

    /**
     * Only send notification on failure or recovery.
     */
    private final Boolean onlyOnFailureOrRecovery;

    /**
     * Include a tiny url to the build that was failing.
     */
    private final Boolean includeUrl;

    /**
     * Whether a text message should be sent or not.
     */
    private final Boolean smsNotification;

    /**
     * Whether a call should be made to the user.
     */
    private final Boolean callNotification;
    
    private final String userList;
    private final Map<String,String> userToPhoneMap;
    private final Map<String,String> substitutionAttributes;

    /**
     * Databound constructor matching the corresponding Jelly configuration items.
     * 
     * @param message                  the message to send
     * @param toList                   the comma separated list of people to send to
     * @param onlyOnFailureOrRecovery  whether to send notification only on failure or recovery
     * @param includeUrl               whether to include a url to the build statu
     * @param smsNotification          whether to send text message
     * @param callNotification         whether to call
     */
    @DataBoundConstructor
    public TwilioNotifier(final String message, final String toList, final String onlyOnFailureOrRecovery,
            final String includeUrl, final String smsNotification, final String callNotification, final String userList) {
        this.message = message;
        this.toList = toList;
        this.onlyOnFailureOrRecovery = convertToBoolean(onlyOnFailureOrRecovery);
        this.includeUrl = convertToBoolean(includeUrl);
        this.smsNotification = convertToBoolean(smsNotification);
        this.callNotification = convertToBoolean(callNotification);
        this.userList = userList;
        userToPhoneMap = parseUserList(userList);
        substitutionAttributes = new HashMap<String,String>();
        
    }

    protected static Map<String,String> parseUserList(final String users)
    {
        Map<String,String> resultMap = new HashMap<String,String>();
        String[] splitUserPairArray = users.split(",");
        for (String userPair: splitUserPairArray)
        {
            String[] splitPair = userPair.split(":");
            resultMap.put(splitPair[0], splitPair[1]);
        }
        return resultMap;
    }
    
    protected static String substituteAttributes(String inputString, Map<String, String> substitutionMap)
    {
        String result = inputString;
        for (String key:substitutionMap.keySet())
        {
            String replaceValue = substitutionMap.get(key);
            result = result.replaceAll(key, replaceValue);
        }
        return result;
    }
    /**
     * Getter for the toList.
     * 
     * @return the toList
     */
    public String getToList() {
        return this.toList;
    }

    /**
     * Getter for the message.
     * 
     * @return the message
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * Getter for callNotification.
     * 
     * @return the callNotification flag
     */
    public Boolean getCallNotification() {
        return this.callNotification;
    }

    /**
     * Getter for smsNotification flag.
     * 
     * @return the smsNotification flag.
     */
    public Boolean getSmsNotification() {
        return this.smsNotification;
    }

    /**
     * Converts a string to a Boolean.
     * 
     * @param string the string to convert to Boolean
     * 
     * @return the Boolean
     */
    private static Boolean convertToBoolean(final String string) {
        Boolean result = null;
        if ("true".equals(string) || "Yes".equals(string)) {
            result = Boolean.TRUE;
        } else if ("false".equals(string) || "No".equals(string)) {
            result = Boolean.FALSE;
        }
        return result;
    }

    /**
     * Returns the include url flag.
     * 
     * @return the includeUrl flag
     */
    public Boolean getIncludeUrl() {
        return this.includeUrl;
    }

    /**
     * Getter for onlyOnFailureOrRecovery flag.
     * 
     * @return the onlyOnFailureOrRecovery flag
     */
    public Boolean getOnlyOnFailureOrRecovery() {
        return this.onlyOnFailureOrRecovery;
    }
    
    /**
     * Getter for the message.
     * 
     * @return the message
     */
    public String getUserList() {
        return this.userList;
    }


    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) {

        try {
            listener.getLogger().println("Perform " + build.getDisplayName());

            if (shouldNotify(build)) {
                final TwilioRestClient client = new TwilioRestClient(getDescriptor().getAccountSID(), getDescriptor()
                        .getAuthToken());
                listener.getLogger().println("Created twilio client");

                String res = "";
                if (build != null) {

                    res = " The project " + build.getProject().getDisplayName();
                    substitutionAttributes.put("%PROJECT%", build.getProject().getDisplayName());
                    substitutionAttributes.put("%BUILD%", build.getDisplayName());
                    substitutionAttributes.put("%STATUS%", build.getResult().toString());

                    res += " and the build " + build.getDisplayName() + " is in status " + build.getResult().toString();
                }
                // Get the main account (The one we used to authenticate the client
                final Account mainAccount = client.getAccount();

                final String messageToSend = substituteAttributes(this.message,substitutionAttributes);
                listener.getLogger().println("Message to send:" + messageToSend);

                // Send an sms
                final SmsFactory smsFactory = mainAccount.getSmsFactory();
                final String[] toArray = getToList().split(",");
                for (final String to : toArray) {
                    final String absoluteBuildURL = getDescriptor().getUrl() + build.getUrl();

                    final String message = messageToSend + "  " + res;
                    String smsMsg = message;
                    if (this.includeUrl.booleanValue()) {
                        smsMsg += " " + createTinyUrl(absoluteBuildURL);
                    }
                    if (this.smsNotification.booleanValue()) {
                        sendSMS(smsMsg, smsFactory, getDescriptor().fromPhoneNumber, to);
                    }
                    if (this.callNotification.booleanValue()) {
                        call(message, mainAccount.getCallFactory(), getDescriptor().fromPhoneNumber, to);
                    }
                }
            } else {
                listener.getLogger().println("Not notifying: " + build.getDisplayName());

            }
        } catch (final Exception t) {
            listener.getLogger().println("Exception " + t);
        }

        return true;
    }

    /**
     * Determine if this build represents a failure or recovery. A build failure
     * includes both failed and unstable builds. A recovery is defined as a
     * successful build that follows a build that was not successful. Always
     * returns false for aborted builds.
     * 
     * @param build
     *            the Build object
     * @return true if this build represents a recovery or failure
     */
    protected boolean isFailureOrRecovery(final AbstractBuild<?, ?> build) {
        if (build.getResult() == Result.FAILURE || build.getResult() == Result.UNSTABLE) {
            return true;
        } else if (build.getResult() == Result.SUCCESS) {
            final AbstractBuild<?, ?> previousBuild = build.getPreviousBuild();
            if (previousBuild != null && previousBuild.getResult() != Result.SUCCESS) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Determine if this build results should be tweeted. Uses the local
     * settings if they are provided, otherwise the global settings.
     * 
     * @param build
     *            the Build object
     * @return true if we should tweet this build result
     */
    protected boolean shouldNotify(final AbstractBuild<?, ?> build) {
        if (this.onlyOnFailureOrRecovery == null) {
            return false;
        } else if (this.onlyOnFailureOrRecovery.booleanValue()) {
            return isFailureOrRecovery(build);
        } else {
            return true;
        }
    }

    /**
     * Sends a text message.
     * 
     * @param message
     * @param smsFactory
     * @param from
     * @param to
     * @throws TwilioRestException
     */
    private void sendSMS(final String message, final SmsFactory smsFactory, final String from, final String to)
            throws TwilioRestException {
        final Map<String, String> smsParams = new HashMap<String, String>();
        smsParams.put("To", to);
        smsParams.put("From", from);
        smsParams.put("Body", message);
        smsFactory.create(smsParams);
    }

    /**
     * Calls and tts the message.
     * 
     * @param message
     * @param callFactory
     * @param from
     * @param to
     * @throws TwilioRestException
     * @throws UnsupportedEncodingException
     */
    private void call(final String message, final CallFactory callFactory, final String from, final String to)
            throws TwilioRestException, UnsupportedEncodingException {
        final Map<String, String> callParams = new HashMap<String, String>();
        callParams.put("To", to);
        callParams.put("From", from);

        final String url = "http://twimlets.com/echo?Twiml="
                + URLEncoder.encode("<Response><Say>" + message + "</Say></Response>", "UTF-8");
        callParams.put("Url", url);
        callFactory.create(callParams);
    }

    private String getUserString(final AbstractBuild<?, ?> build) throws IOException {
        final StringBuilder userString = new StringBuilder("");
        final Set<User> culprits = build.getCulprits();
        final ChangeLogSet<? extends Entry> changeSet = build.getChangeSet();
        if (culprits.size() > 0) {
            for (final User user : culprits) {
                final MobilePhoneProperty tid = user.getProperty(MobilePhoneProperty.class);
                if (tid.getMobilePhone() != null) {
                    userString.append("@").append(tid.getMobilePhone()).append(" ");
                }
            }
        } else if (changeSet != null) {
            for (final Entry entry : changeSet) {
                final User user = entry.getAuthor();
                final MobilePhoneProperty tid = user.getProperty(MobilePhoneProperty.class);
                if (tid.getMobilePhone() != null) {
                    userString.append("@").append(tid.getMobilePhone()).append(" ");
                }
            }
        }
        return userString.toString();
    }

    /**
     * Creates a tiny url out of a longer url.
     * 
     * @param url
     * @return
     * @throws IOException
     */
    private static String createTinyUrl(final String url) throws IOException {
        final HttpClient client = new HttpClient();
        final GetMethod gm = new GetMethod("http://tinyurl.com/api-create.php?url=" + url.replace(" ", "%20"));

        final int status = client.executeMethod(gm);
        if (status == HttpStatus.SC_OK) {
            return gm.getResponseBodyAsString();
        } else {
            throw new IOException("Non-OK response code back from tinyurl: " + status);
        }

    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());

        public String accountsid;
        public String authtoken;

        public String fromPhoneNumber;

        public String getFromPhoneNumber() {
            return this.fromPhoneNumber;
        }

        public String hudsonUrl;

        public DescriptorImpl() {
            super(TwilioNotifier.class);
            load();
        }

        @Override
        public boolean configure(final StaplerRequest req, final JSONObject formData) throws FormException {
            // set the booleans to false as defaults

            this.accountsid = formData.getString("accountSID");
            this.authtoken = formData.getString("authtoken");
            this.fromPhoneNumber = formData.getString("fromPhoneNumber");
            save();
            return super.configure(req, formData);
        }

        @Override
        public String getDisplayName() {
            return "TwilioNotifier";
        }

        public String getAccountSID() {
            return this.accountsid;
        }

        public String getAuthToken() {
            return this.authtoken;
        }

        public String getUrl() {
            return this.hudsonUrl;
        }

        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public Publisher newInstance(final StaplerRequest req, final JSONObject formData) throws FormException {
            if (this.hudsonUrl == null) {
                // if Hudson URL is not configured yet, infer some default
                this.hudsonUrl = Functions.inferHudsonURL(req);
                save();
            }
            return super.newInstance(req, formData);
        }
    }

}
