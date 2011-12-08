package com.twilio.jenkins;



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

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private String mobilePhone;

    public MobilePhoneProperty() {
    }

    @DataBoundConstructor
    public MobilePhoneProperty(String mobilePhone) {
        this.mobilePhone = mobilePhone;
    }

    public UserPropertyDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    @Exported
    public User getUser() {
        return user;
    }

    @Exported
    public String getMobilePhone() {
        return mobilePhone;
    }

    public void setMobilePhone(String mobilePhone) {
        this.mobilePhone = mobilePhone;
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
                return req.bindJSON(MobilePhoneProperty.class, formData);
            } else {
                return new MobilePhoneProperty();
            }
        }

        @Override
        public UserProperty newInstance(User user) {
            return null;
        }
    }
}