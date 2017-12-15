package me.termer.jserver;

public class Command {
	//Type
	private String t = null;
	//Data
	private String d = null;
	public Command(String type, String data) {
		if(type == null || type.isEmpty()) {
			throw new IllegalArgumentException("type connot be null");
		}
		t = type;
		if(data == null) {
			d = "";
		} else {
			d = data;
		}
	}
	public String getType() {
		return t;
	}
	public String getData() {
		return d;
	}
	
	public void setType(String type) {
		t = type;
	}
	public void setData(String data) {
		d = data;
	}
	
	public String toString() {
		String tmp = "";
		if(t.endsWith(" ")) {
			tmp = t+d;
		} else {
			tmp = t+" "+d;
		}
		return tmp;
	}
}