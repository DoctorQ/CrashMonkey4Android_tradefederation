package com.android.tradefed.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import com.android.tradefed.log.LogUtil.CLog;
import com.sun.mail.smtp.SMTPTransport;

/*
 * reference http://xst4002.iteye.com/blog/1725732
 * a helper class to send mail platform independent
 */
public class SmtpMail implements IEmail {
	private static final String LOG_TAG = "SmtpMail";
	private static final String PROTO = "smtp";
	private boolean mIsAuth = false;
	private String mHost;
	private String mUser;
	private String mPasswd;
	
	public SmtpMail() {
		
	}
	
	public SmtpMail(String host) {
		this(host,false);
	}

	public SmtpMail(String host, boolean isAuth) {
		mHost = host;
		mIsAuth = isAuth;
	}
	
	public void setUser(String user) {
		mUser = user;
	}
	
	public void setPassword(String password) {
		mPasswd = password;
	}
	
	public void setHost(String host) {
		mHost = host;
	}
	
	public void setAuth(boolean auth) {
		mIsAuth = auth;
	}
	
	@Override
	public void send(Message msg) throws IllegalArgumentException, IOException {
		Properties props = new Properties();
		props.setProperty("mail.smtp.host", mHost);
		props.setProperty("mail.smtp.auth", String.valueOf(mIsAuth));
		
		Session session = Session.getDefaultInstance(props);
		
		MimeMessage message = new MimeMessage(session);
		
		SMTPTransport transport = null;
		try {
			if(msg.getSender() != null) {
				message.setFrom(getInternetAddress(msg.getSender()));
			}
			message.setRecipients(javax.mail.Message.RecipientType.TO, getInternetAddresses(msg.getTo()));
			if(msg.getCc() != null) {
				message.setRecipients(javax.mail.Message.RecipientType.CC, getInternetAddresses(msg.getCc()));
			}
			if(msg.getBcc() != null) {
				message.setRecipients(javax.mail.Message.RecipientType.BCC, getInternetAddresses(msg.getBcc()));
			}
			if(msg.getSubject() != null) {
				message.setSubject(msg.getSubject());
			}
			
			Multipart multipart = new MimeMultipart();
			
			MimeBodyPart messageBodyPart = new MimeBodyPart();
			if(msg.getBody() != null) {
				if(msg.isHtml()) {
					messageBodyPart.setContent(msg.getBody(), Message.HTML + (msg.getCharset() == null ? "" : ";charset="+msg.getCharset()));
				} else {
					if(msg.getCharset() != null)
						messageBodyPart.setText(msg.getBody(), msg.getCharset());
					else {
						messageBodyPart.setText(msg.getBody());
					}
				}
				multipart.addBodyPart(messageBodyPart);
			}
			
			for(File file:msg.getAttach()) {
				messageBodyPart = new MimeBodyPart();
				messageBodyPart.attachFile(file);
				multipart.addBodyPart(messageBodyPart);
			}
			
			for(File file : msg.getDisposition()) {
				messageBodyPart = new MimeBodyPart();
				messageBodyPart.setDataHandler(new DataHandler(new FileDataSource(file)));
				messageBodyPart.setFileName(file.getName());
				messageBodyPart.setDisposition(MimeBodyPart.INLINE);
				multipart.addBodyPart(messageBodyPart);
			}
			
			message.setContent(multipart);	
			
			message.setSentDate(new Date());
			
			transport = (SMTPTransport) session.getTransport(PROTO);
			if(mIsAuth) {
				transport.connect(mHost, mUser, mPasswd);
			} else {
				transport.connect();
			}
			transport.sendMessage(message, message.getAllRecipients());
		} catch (AddressException e) {
			CLog.e(e);
		} catch (MessagingException e) {
			CLog.e(e);
		} finally {
			if(transport != null) {
				try {
					transport.close();
				} catch (MessagingException e) {
					CLog.e(e);
				}
			}
		}
		 
	}

	private InternetAddress getInternetAddress(String address) throws AddressException {
		if(address == null) {
			throw new NullPointerException("address is null");
		}
		return new InternetAddress(address);
	}
	
	private InternetAddress[] getInternetAddresses(Collection<String> addresses) throws AddressException {
		if(addresses == null) {
			throw new NullPointerException("addresses is null");
		}
		List<InternetAddress> ret = new ArrayList<InternetAddress>();
		for(String addr:addresses) {
			ret.add(new InternetAddress(addr));
		}
		return ret.toArray(new InternetAddress[0]);
	}
	
	public static void main(String[] argv) throws IOException {
		SmtpMail mail = new SmtpMail();
		mail.setAuth(true);
		mail.setHost("mail.spreadtrum.com");
		mail.setUser("jian.xiong");
		mail.setPassword("123456");
		
		Message msg = new Message();
		msg.setSender("jian.xiong@spreadtrum.com");
		msg.setTos(new String[] {"jian.xiong@spreadtrum.com"});
		msg.setHtml(true);
		msg.setCharset("utf-8");
		msg.addDisposition(new File("c:\\logo.gif"));
		msg.addDisposition(new File("c:\\newrule-green.png"));
		msg.addAttach(new File("c:\\ccc.jar"));
		msg.setBody(FileUtil.readStringFromFile(new File("c:\\testResult.html"), "utf-8"));
		
		mail.send(msg);
	}
}
