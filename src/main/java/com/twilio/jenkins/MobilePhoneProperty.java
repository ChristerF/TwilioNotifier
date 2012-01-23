package com.twilio.jenkins;



import hudson.Extension;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * UserProperty class which contains a user's mobile phone number.
 * 
 */
@ExportedBean(defaultVisibility = 999)
public class MobilePhoneProperty extends UserProperty {

	@Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private String mobilephone;

    public MobilePhoneProperty() {
    }

    @DataBoundConstructor
    public MobilePhoneProperty(String mobilePhone) {
        this.mobilephone = mobilePhone;
    }

    public UserPropertyDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    @Exported
    public User getUser() {
        return user;
    }

    @Exported
    public String getMobilephone() {
        return mobilephone;
    }

    public void setMobilephone(String mobilephone) {
        this.mobilephone = mobilephone;
    }

    public static final class DescriptorImpl extends UserPropertyDescriptor {
        public DescriptorImpl() {
            super(MobilePhoneProperty.class);
        }

        @Override
        public String getDisplayName() {
            return "Mobile Phone";
        }

        @Override
        public MobilePhoneProperty newInstance(StaplerRequest req, JSONObject formData)
                throws hudson.model.Descriptor.FormException {
            if (formData.has("mobilephone")) {
				return new MobilePhoneProperty((String) formData.get("mobilephone"));
            } else {
                return new MobilePhoneProperty();
            }
        }

        @Override
        public UserProperty newInstance(User user) {
            return new MobilePhoneProperty();
        }
    }
}