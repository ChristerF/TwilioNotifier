package com.twilio.jenkins;

import hudson.Extension;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.*;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.twilio.sdk.TwilioRestClient;
import com.twilio.sdk.TwilioRestException;
import com.twilio.sdk.resource.factory.CallFactory;
import com.twilio.sdk.resource.factory.SmsFactory;
import com.twilio.sdk.resource.instance.Account;

/**
 * A {@link TwilioNotifier} is a {@link Notifier} that uses the Rest API of
 * communications service provider {@linkplain "http://www.twilio.com/} to send
 * text messages and make calls with the build status of individual builds.
 *
 * This notifier was inspired in features and design by the Twitter Notifier
 * plugin.
 *
 * @author Christer Fahlgren (christer@twilio.com)
 */
public class TwilioNotifier extends Notifier {
	private static final Logger LOGGER = Logger.getLogger(TwilioNotifier.class.getName());

    /**
     * The message to send/read to the recipient.
     */
    private final String message;

    /**
     * The list of phone numbers, comma separated of people who should receive
     * the communications.
     */
    private final String toList;

    private final Boolean sendToCulprits;
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

    private final String culpritMessage;
    private final Map<String, String> substitutionAttributes;

    /**
     * Databound constructor matching the corresponding Jelly configuration
     * items.
     *
     * @param message
     *            the message to send
     * @param toList
     *            the comma separated list of people to send to
     * @param onlyOnFailureOrRecovery
     *            whether to send notification only on failure or recovery
     * @param includeUrl
     *            whether to include a url to the build statu
     * @param smsNotification
     *            whether to send text message
     * @param callNotification
     *            whether to call
	 * @param userList
	 * 			  list of users to call or sms
	 * @param sendToCulprits
	 * 			  whether or not to send messages to those who broke the build
	 * @param culpritMessage
	 * 			  message to send to culprits
     */
    @DataBoundConstructor
    public TwilioNotifier(final String message, final String toList, final String onlyOnFailureOrRecovery,
            final String includeUrl, final String smsNotification, final String callNotification,
            final String sendToCulprits, final String culpritMessage) {
        this.message = message;
        this.toList = toList;
        this.onlyOnFailureOrRecovery = convertToBoolean(onlyOnFailureOrRecovery);
        this.includeUrl = convertToBoolean(includeUrl);
        this.smsNotification = convertToBoolean(smsNotification);
        this.callNotification = convertToBoolean(callNotification);
        this.sendToCulprits = convertToBoolean(sendToCulprits);
        this.culpritMessage = culpritMessage;

        substitutionAttributes = new HashMap<String, String>();

    }

    protected static String substituteAttributes(String inputString, Map<String, String> substitutionMap) {
        String result = inputString;
        for (String key : substitutionMap.keySet()) {
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
     * Validates the toList.
     *
     * @param value the toList to validate
     * @return {@link hudson.util.FormValidation#ok()} if valid, {@link FormValidation#error(String)} if not valid
     */
    @SuppressWarnings({"UnusedDeclaration"})
	public FormValidation doCheckToList(@QueryParameter String value) {
        if (validatePhoneNoList(value))
            return FormValidation.ok();
        else
            return FormValidation
                    .error("The to list must consist of at least one phone number. Multiple numbers are comma separated.");
    }

    private boolean validatePhoneNoList(String toList) {

        String[] nrs = toList.split(",");
        if (nrs == null)
            return false;
        for (String number : nrs) {
            if (!number.matches("^([0-9\\(\\)\\/\\+ \\-]*)$"))
                return false;
        }
        return false;
    }

    /**
     * Getter for the message.
     *
     * @return the message
     */
	@SuppressWarnings({"UnusedDeclaration"})
    public String getMessage() {
        return this.message;
    }

    /**
     * Getter for the culprit message.
     *
     * @return the culprit message
     */
	@SuppressWarnings({"UnusedDeclaration"})
    public String getCulpritMessage() {
        return this.culpritMessage;
    }

    /**
     * Getter for callNotification.
     *
     * @return the callNotification flag
     */
	@SuppressWarnings({"UnusedDeclaration"})
    public Boolean getCallNotification() {
        return this.callNotification;
    }

    /**
     * Getter for smsNotification flag.
     *
     * @return the smsNotification flag.
     */
	@SuppressWarnings({"UnusedDeclaration"})
    public Boolean getSmsNotification() {
        return this.smsNotification;
    }

    /**
     * Getter for sendToCulprits flag.
     *
     * @return the sendToCulprits flag.
     */
	@SuppressWarnings({"UnusedDeclaration"})
    public Boolean getSendToCulprits() {
        return this.sendToCulprits;
    }

    /**
     * Converts a string to a Boolean.
     *
     * @param string
     *            the string to convert to Boolean
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
	@SuppressWarnings({"UnusedDeclaration"})
    public Boolean getIncludeUrl() {
        return this.includeUrl;
    }

    /**
     * Getter for onlyOnFailureOrRecovery flag.
     *
     * @return the onlyOnFailureOrRecovery flag
     */
	@SuppressWarnings({"UnusedDeclaration"})
    public Boolean getOnlyOnFailureOrRecovery() {
        return this.onlyOnFailureOrRecovery;
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) {
		if(build == null) return false;

		substitutionAttributes.put("%PROJECT%", build.getProject().getDisplayName());
		substitutionAttributes.put("%BUILD%", build.getDisplayName());
		substitutionAttributes.put("%STATUS%", build.getResult().toString());


		if (!shouldNotify(build)) {
			LOGGER.warning("Not notifying: " + build.getDisplayName());
			return true;
		}
		LOGGER.info("Notifying: " + build.getProject().getDisplayName() + " " + build.getDisplayName());

		// Get the main account (The one we used to authenticate the client)
		final TwilioRestClient client = new TwilioRestClient(getDescriptor().getAccountSID(), getDescriptor().getAuthToken());
		final Account mainAccount = client.getAccount();

		Set<User> culpritList = getCulpritList(build);
		String culpritString = culpritStringFromList(culpritList);
		LOGGER.info("Culprits: " + culpritString);
		substitutionAttributes.put("%CULPRITS%", culpritString);

		// Send an sms
		final SmsFactory smsFactory = mainAccount.getSmsFactory();
		final CallFactory callFactory = mainAccount.getCallFactory();
		final String[] toArray = getToList().split(",");
		final String url = getDescriptor().getUrl() + build.getUrl();
		
		sendToToNumbers(toArray, url, callFactory, smsFactory);

		if (sendToCulprits) {
			sendToCulprits(url, callFactory, culpritList, smsFactory);
		}

        return true;
    }

	private void sendToCulprits(String absoluteBuildURL, CallFactory callFactory, Set<User> culpritList, SmsFactory smsFactory) {
		LOGGER.info("Sending to culprits");
		if(culpritList == null || culpritList.isEmpty()) {
			LOGGER.info("Not sending messages to culprits since there aren't any");
			return;
		}
		for (final User to : culpritList) {
			if(to == null) {
				LOGGER.warning("There was a null value in the culprit list");
				continue;
			}

			MobilePhoneProperty property = to.getProperty(MobilePhoneProperty.class);
			if(property == null) {
				LOGGER.warning("User " + to.getDisplayName() + " doesn't have the MobilePhoneProperty");
				continue;
			}

			String toNumber = property.getMobilephone();
			if(toNumber == null || toNumber.isEmpty()) {
				LOGGER.warning("User " + to.getDisplayName() + " doesn't have a phone number listed");
				continue;
			}

			LOGGER.info("Preparing to notify to " + to.getDisplayName() + " at " + toNumber);

			final Map<String, String> localSubAttrs = new HashMap<String, String>(substitutionAttributes);
			localSubAttrs.put("%CULPRIT-NAME%", to.getDisplayName());

			String messageToSend;
			if (this.culpritMessage == null || this.culpritMessage.trim().isEmpty()) {
				LOGGER.info("Empty culprit message. Using the generic message instead");
				messageToSend = substituteAttributes(this.message, localSubAttrs);
			} else {
				LOGGER.info("Using the specified culprit message.");
				messageToSend = substituteAttributes(this.culpritMessage, localSubAttrs);
			}

			final String message = messageToSend;

			if (this.smsNotification) {
				LOGGER.info("Sending SMS notification to culprit");
				String smsMsg = message;
				if (this.includeUrl) {
					try {
						LOGGER.info("Tinyifying URL");
						smsMsg += " " + createTinyUrl(absoluteBuildURL);
					} catch (IOException e) {
						logException(e);
					}
				}

				try {
					LOGGER.info("Sending SMS message to " + to.getDisplayName() + "(" + toNumber + "): " + smsMsg);
					sendSMS(smsMsg, smsFactory, getDescriptor().fromPhoneNumber, toNumber);
				} catch (TwilioRestException e) {
					logException(e);
				}
			}
			if (this.callNotification) {
				try {
					LOGGER.info("Sending phone call message to " + to.getDisplayName() + "(" + toNumber + "): " + message);
					call(message, callFactory, getDescriptor().fromPhoneNumber, toNumber);
				} catch (TwilioRestException e) {
					logException(e);
				} catch (UnsupportedEncodingException e) {
					logException(e);
				}
			}
		}
	}

	private void sendToToNumbers(String[] toArray, String absoluteBuildURL, CallFactory callFactory, SmsFactory smsFactory) {
		if (toArray != null) {
			LOGGER.info("Sending to To List");
			for (String to : toArray) {
				if(to == null || to.trim().isEmpty()) {
					LOGGER.info("Not sending to To list since it was empty");
					continue;
				}
				to = to.trim();
				LOGGER.info("Sending to " + to);

				final String message = substituteAttributes(this.message, this.substitutionAttributes);

				if (this.smsNotification) {
					LOGGER.info("Sending SMS to " + to);
					String smsMsg = message;
					if (this.includeUrl) {
						try {
							smsMsg += " " + createTinyUrl(absoluteBuildURL);
						} catch (IOException e) {
							logException(e);
						}
					}

					try {
						LOGGER.info("Sending SMS message to " + to + " " + smsMsg);
						sendSMS(smsMsg, smsFactory, getDescriptor().fromPhoneNumber, to);
					} catch (TwilioRestException e) {
						logException(e);
					}
				}
				if (this.callNotification) {
					LOGGER.info("Sending Call to " + to);
					try {
						call(message, callFactory, getDescriptor().fromPhoneNumber, to);
					} catch (TwilioRestException e) {
						logException(e);
					} catch (UnsupportedEncodingException e) {
						logException(e);
					}
				}
			}
		}
	}

	protected static String culpritStringFromList(Collection<? extends ModelObject> culprits) {
		if(culprits == null || culprits.size() <= 0) return "";
		StringBuilder sb = new StringBuilder();
		int c = culprits.size() - 1;
		for (ModelObject user : culprits) {
			String name = user.getDisplayName();

			if(c == 0 && sb.length() > 0) {
				//last item in a list of more than one
				sb.append(" and ").append(name);
			} else {
				sb.append(" ").append(name);
			}

			c--;
		}
        return sb.substring(1); //trim off the space at the start
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
            return previousBuild != null && previousBuild.getResult() != Result.SUCCESS;
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
		return this.onlyOnFailureOrRecovery != null && (!this.onlyOnFailureOrRecovery || isFailureOrRecovery(build));
    }

    /**
     * Sends a text message.
     *
     * @param message Message to send to the phone number
     * @param smsFactory Twilio SMS Factory from the Twilio main account
     * @param from the phone number to send this from
     * @param to the phone number to send this to
     * @throws TwilioRestException Thrown when there is an error from the Twilio servers
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
     * @param message Message to be spoken
     * @param callFactory Twilio Call Factory from the Twilio main account
     * @param from the phone number to send this from
     * @param to the phone number to send this to
     * @throws TwilioRestException Thrown when there is an error from the Twilio servers
     * @throws UnsupportedEncodingException The message is URL encoded. This is the exception thrown if there's an error when this happens
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

    private Set<User> getCulpritList(final AbstractBuild<?, ?> build) {
		final Set<User> culprits = new HashSet<User>(build.getCulprits());
		if(culprits.size() <= 0) {
			ChangeLogSet<? extends Entry> changeSet = build.getChangeSet();
			if(changeSet != null) {
				for (final Entry entry : changeSet) {
					User author = entry.getAuthor();
					culprits.add(author);
				}
			}
		}
		return culprits;
    }

    /**
     * Creates a tiny url out of a longer url.
     *
     * @param url URL to tiny-ify
     * @return Returns the url returned from tinyurl.com
     * @throws IOException thrown when there are any network connection problems to the tinyurl.com servers
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

	private void logException(Throwable e) {
		StringBuilder sb = new StringBuilder(e.getMessage()).append("\n");
		StackTraceElement[] stackTrace = e.getStackTrace();
		for (StackTraceElement element : stackTrace) {
			sb.append("\t").append(element.toString()).append("\n");
		}
		LOGGER.severe(sb.toString());
	}

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public String accountsid;
        public String authtoken;

        public String fromPhoneNumber;

        @SuppressWarnings({"UnusedDeclaration"})
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
