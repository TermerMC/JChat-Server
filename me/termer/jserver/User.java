package me.termer.jserver;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;

public class User {
	//Print Writer
	private PrintWriter p = null;
	//Password
	private String pass = null;
	//Name
	private String n = null;
	//Is logged in?
	private boolean l = false;
	//Is admin?
	private boolean a = false;
	
	public User(PrintWriter pw, String name) {
		p = pw;
		n = name;
		if(Main.passwords.containsKey(n)) {
			pass = Main.passwords.get(n);
		}
		if(!hasPassword()) {
			l = true;
		}
		if(Main.admins.contains(n)) {
			a = true;
		}
	}
	
	public PrintWriter getOutput() {
		return p;
	}
	public String getName() {
		return n;
	}
	public String getPassword() {
		return pass;
	}
	
	public boolean hasPassword() {
		boolean has = false;
		if(pass != null && !pass.isEmpty()) {
			has = true;
		}
		return has;
	}
	public boolean isLoggedIn() {
		return l;
	}
	public boolean isAdmin() {
		return a;
	}
	
	public void setName(String name) {
		n = name;
	}
	public void setLoggedIn(boolean loggedIn) {
		l = loggedIn;
	}
	public void setPassword(String password) {
		pass = password;
	}
	public void setAdmin(boolean admin) {
		a = admin;
	}
	
	public void sendMessage(String msg) {
		p.println("MESSAGE "+msg);
	}
	public void sendCommand(Command cmd) {
		p.println(cmd.toString());
	}
	public void sendRaw(String raw) {
		p.println(raw);
	}
	public void kick() {
		p.println("KICK");
		p.flush();
		p.close();
	}
	
	public static void broadcast(String msg) {
		for (User user : Main.users) {
			user.sendMessage(msg);
		}
	}
	
	public static User[] getUsers() {
		return Main.users.toArray(new User[0]);
	}
	public static String[] getNames() {
		ArrayList<String> tmp = new ArrayList<String>();
		for(int i = 0; i < getUsers().length; i++) {
			tmp.add(getUsers()[i].getName());
		}
		return tmp.toArray(new String[0]);
	}
	public static HashSet<String> getNamesAsSet() {
		HashSet<String> tmp = new HashSet<String>();
		for(int i = 0; i < getUsers().length; i++) {
			tmp.add(getUsers()[i].getName());
		}
		return tmp;
	}
	public static User getUserByName(String name) {
		User tmp = null;
		for(User user : Main.users) {
			if(user.getName().equalsIgnoreCase(name)) {
				tmp = user;
			}
		}
		return tmp;
	}
}