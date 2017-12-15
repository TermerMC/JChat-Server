package me.termer.jserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import net.termer.utils.Utils;

public class Main {
	private static String IP = "";
    private static final int PORT = 9001;
    public static HashSet<User> users = new HashSet<User>();
    public static HashMap<String,String> passwords = new HashMap<String,String>();
    public static HashSet<String> admins = new HashSet<String>();
    public static String[] motd = {};
    public static void main(String[] args) throws Exception {
        System.out.print("Starting...");
        ServerSocket listener = null;
        //Check files
        try {
        	//Ip file
        	File ipFile = new File("ip.txt");
        	if(!ipFile.exists()) {
        		ipFile.createNewFile();
        	}
        	try {
        		IP = Utils.getFile(new File("ip.txt"))[0];
        	} catch(Exception e) {}
        	//Passwords file
        	File passFile = new File("passwords.jc");
        	if(!passFile.exists()) {
        		passFile.createNewFile();
        	}
        	String[] lns = Utils.getFile(passFile);
        	for(int i = 0; i < lns.length; i++) {
        		if(!lns[i].isEmpty()) {
        			passwords.put(lns[i].split(":")[0], lns[i].split(":")[1]);
        		}
        	}
        	//Admins file
        	File adminFile = new File("admins.txt");
        	if(!adminFile.exists()) {
        		adminFile.createNewFile();
        	}
        	String[] lns1 = Utils.getFile(adminFile);
        	for(int i = 0; i < lns1.length; i++) {
        		admins.add(lns1[i]);
        	}
        	File motdFile = new File("motd.txt");
        	if(!motdFile.exists()) {
        		motdFile.createNewFile();
        	}
        	motd = Utils.getFile(motdFile);
        } catch(Exception e){
        	System.err.println("Error while initializing files:");
        	e.printStackTrace();
        }
        if(IP.isEmpty() && IP != null) {
        	listener = new ServerSocket(PORT);
        } else {
        	try {
        		InetAddress bindIp = InetAddress.getByName(IP);
        		listener = new ServerSocket(PORT, 50, bindIp);
        	} catch(Exception e) {
        		System.out.println("Could not bind to IP: "+IP);
        		//End with error code 1
        		System.exit(1);
        	}
        }
        System.out.println("Done");
        System.out.println("Running on "+listener.getInetAddress().getHostName()+":"+listener.getLocalPort());
        try {
            while (true) {
                new Handler(listener.accept()).start();
            }
        } finally {
            listener.close();
        }
    }

    //Handler thread. This thread is spawned for each user by the listener loop.
    private static class Handler extends Thread {
        private String name;
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private User user;
        
        //Handler for a single client. All the interesting stuff is done here.
        public Handler(Socket socket) {
            this.socket = socket;
        }

        //Requests a name from the client until a unique one is found
        public void run() {
            try {
                // Create character streams for the socket.
                in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Request a name from this client.  Keep requesting until
                // a name is submitted that is not already used.  Note that
                // checking for the existence of a name and adding the name
                // must be done while locking the set of names.
                while (true) {
                    out.println("SUBMITNAME");
                    name = in.readLine().trim();
                    if (name == null || name.trim().isEmpty()) {
                        return;
                    }
                    synchronized (User.getNamesAsSet()) {
                        if (!User.getNamesAsSet().contains(name)) {
                        	user = new User(out,name);
                            users.add(user);
                            break;
                        }
                    }
                }
                
                //Join message
                System.out.println("[JOIN] "+name);
                if(user.isLoggedIn()) {
                	User.broadcast(name+" joined");
                }
                user.sendRaw(CommandType.NAME_ACCEPTED);
                
                //Display MotD
                for(int i = 0; i < motd.length; i++) {
                	user.sendMessage(motd[i].replaceAll("@name", name).replaceAll("@users", Integer.toString(users.size())));
                }
                
                if(!user.isLoggedIn()) {
                	user.sendMessage("This name is registered. Use /login <password> to log in.");
                	user.sendMessage("You cannot chat or run commands until you have logged in");
                } else if(!user.hasPassword()) {
                	user.sendMessage("This name is not registered. Register it by typing /password <password>");
                }
                // Accept messages from this client and broadcast them.
                // Handle file transfer from this client
                // Ignore other clients that cannot be broadcasted to.
                Long rf = 0L;
                User sendto = null;
                while (true) {
                	String input = "";
                	try {
                		if(rf>0) {
                			System.out.println("Beginning file transfer to "+sendto.getName());
                			while(rf>0) {
                				int c = in.read();
                				sendto.getOutput().write(c);
                				rf--;
                			}
                			user.sendMessage("File sent successfully!");
                			sendto.sendMessage("You have received the file!");
                		}
                    	input = in.readLine().trim();
                    	while(input==null) {
                    		input=in.readLine().trim();
                    	}
                	} catch(NullPointerException e) {
                		throw new IOException("User logged out");
                	}
                    if (input == null) {
                        return;
                    }
                    if(!input.isEmpty()) {
                    	if(!input.startsWith("/")) {
                    		if(user.isLoggedIn()) {
                    			if(input.startsWith("SENDFILEOK")) {
                    				User us = User.getUserByName(input.substring(11));
                    				if(us==null) {
                    					user.sendMessage("User "+input.substring(11)+" is not logged in!");
                    				} else {
                    					user.sendMessage("User "+us.getName()+" is online. Waiting for their confirmation.");
                    					us.sendCommand(new Command(CommandType.OKFILE, user.getName()));
                    				}
                    			} else if(input.startsWith("ACCEPTFILE")) {
                    				User us = User.getUserByName(input.substring(11));
                    				if(us==null) {
                    					user.sendMessage("Sender is no longer online.");
                    				} else {
                    					us.sendMessage("Confirmation received.");
                    					us.sendCommand(new Command(CommandType.FILE_ACCEPTED, user.getName()));
                    				}
                    			} else if(input.startsWith("SENDFILE")) {
                    				user.sendMessage("Due to a bug in the chat software, you need to keep");
                    				user.sendMessage("chatting until the other user has receive their file.");
                    				User us = User.getUserByName(input.split(" ")[1]);
                    				rf=Long.parseLong(input.split(" ")[2]);
                    				
                    				if(us==null) {
                    					user.sendMessage("User "+input.split(" ")[1]+" is no longer online. File transfer cancelled.");
                    				} else {
                    					sendto=us;
                    					user.sendMessage("Sending file to "+us.getName()+"...");
                    					System.out.println("Sending FILE header");
                    					us.sendCommand(new Command(CommandType.FILE, Long.toString(rf)));
                    					System.out.println("File size: "+Long.toString(rf));
                    				}
                    			} else {
                    				System.out.println("[CHAT] "+name+": "+input.trim());
                    				if(user.hasPassword()) {
                    					if(user.isAdmin()) {
                    						User.broadcast("[Admin] "+name+": "+input.trim());
                    					} else {
                    						User.broadcast("[User] "+name+": "+input.trim());
                    					}
                    				} else {
                    				User.broadcast("[Guest] "+name+": "+input.trim());
                    				}
                    			}
                    		}
                    	} else {
                    		handleCommand(input.substring(1), input.substring(1).split(" ")[0]);
                    	}
                    }
                }
            } catch (IOException e) {} finally {
            	
                // This client is going down!  Remove its name and its print
                // writer from the sets, and close its socket.
                if (name != null && out != null) {
                    users.remove(user);
                }
                System.out.println("[LEAVE] "+name);
                if(user.isLoggedIn()) {
                	User.broadcast(name+" left");
                }
                try {
                    socket.close();
                } catch (IOException e) {
                	System.err.println("Internal error while closing socket for "+name);
                }
            }
        }
        private void handleCommand(String cmd, String cmdName) {
        	if(user.isLoggedIn()) {
        	try {
        		if(cmdName.equalsIgnoreCase("kickall") && user.isAdmin()) {
        			for(User u : users) {
        				u.sendMessage("You have been kicked!");
        				u.kick();
        			}
        			users = new HashSet<User>();
        			System.out.println("All users have been kicked");
        		} else if(cmdName.equalsIgnoreCase("kick") && user.isAdmin()) {
        			try {
        				User kickee = null;
        				for(User u : users) {
        					if(u.getName().equalsIgnoreCase(cmd.split(" ")[1])) {
        						kickee = u;
        					}
        				}
        				if(kickee != null) {
        					kickee.sendMessage("You have been kicked by "+user.getName());
        					kickee.kick();
        					user.sendMessage(kickee.getName()+" has been kicked");
        				} else {
        					user.sendMessage("\""+cmd.split(" ")[1]+"\" isn't online");
        				}
        			} catch(Exception e) {
        				user.sendMessage("You have been kicked!");
        				user.kick();
        			}
        		} else if(cmdName.equalsIgnoreCase("stop") && user.isAdmin()) {
        			user.sendMessage("Shutting down server...");
        			System.out.print("Shutting down...");
        			String[] ks = passwords.keySet().toArray(new String[0]);
        			String[] vs = passwords.values().toArray(new String[0]);
        			ArrayList<String> f = new ArrayList<String>();
        			for(int i = 0; i < ks.length; i++) {
        				f.add(ks[i]+":"+vs[i]);
        			}
        			Utils.writeFile("passwords.jc", f.toArray(new String[0]), false);
        			Utils.writeFile("admins.txt", admins.toArray(new String[0]), false);
        			System.out.println("Done");
        			System.exit(1);
        		} else if(cmdName.equalsIgnoreCase("password")) {
        			try {
        				if(user.hasPassword()) {
        					user.sendMessage("You already have a password!");
        				} else {
        					passwords.put(name, cmd.split(" ")[1]);
        					user.sendMessage("You have now registered with a password. Don't forget it!");
        				}
        				user.setPassword(passwords.get(name));
        			} catch(Exception e) {
        				user.sendMessage("Usage: /password <password>");
        			}
        		} else if(cmdName.equalsIgnoreCase("addadmin")) {
        			try {
        				admins.add(cmd.split(" ")[1]);
        				User[] u = users.toArray(new User[0]);
        				for(int i = 0; i < u.length; i++) {
        					if(u[i].getName().equalsIgnoreCase(cmd.split(" ")[1])) {
        						u[i].setAdmin(true);
        					}
        				}
        				user.sendMessage("User is now an admin");
        			} catch(Exception e) {
        				user.sendMessage("Usage: addadmin <user>");
        			}
        		} else if(cmdName.equalsIgnoreCase("removeadmin")) {
        			try {
        				User[] u = users.toArray(new User[0]);
        				for(int i = 0; i < u.length; i++) {
        					if(u[i].getName().equalsIgnoreCase(cmd.split(" ")[1])) {
        						u[i].setAdmin(false);
        						user.sendMessage("User is no longer an admin");
        					}
        				}
        				admins.remove(cmd.split(" ")[1]);
        			} catch(Exception e) {
        				user.sendMessage("Usage: removeadmin <user>");
        			}
        		} else if(cmdName.equalsIgnoreCase("help")) {
        			user.sendMessage("=============================================================");
        			if(user.isAdmin()) {
        				user.sendMessage("Admin Commands:");
            			user.sendMessage("  kick [user] - Kicks your self or another user");
            			user.sendMessage("  kickall - Kicks all users");
            			user.sendMessage("  stop - Stops the server");
            			user.sendMessage("  addadmin <user> - Makes the specified user an admin");
            			user.sendMessage("  removeadmin <user> - Remove the specified admin");
            			user.sendMessage("Standard Commands:");
        			}
        			user.sendMessage("  password <password> - Registers a user with the specified password");
        			user.sendMessage("  login - Logs a user in");
        			user.sendMessage("=============================================================");
        		} else {
        			user.sendMessage("Invalid command. Type /help for available commands.");
        		}
        	} catch(Exception e) {
        		user.sendMessage("Error while executing command");
        		System.err.println("Could not execute /"+cmd+" because:");
        		e.printStackTrace();
        	}
        	} else {
        	if(cmdName.equalsIgnoreCase("login")) {
        		if(user.isLoggedIn()) {
        			user.sendMessage("You are already logged in!");
        		} else {
        			try {
        				if(cmd.split(" ")[1].equalsIgnoreCase(user.getPassword())) {
        					user.setLoggedIn(true);
        					user.sendMessage("You are now logged in!");
        					User.broadcast(name+" joined");
        				} else {
        					user.sendMessage("Wrong password!");
        				}
        			} catch(Exception e) {
        				user.sendMessage("Usage: /login <password>");
        			}
        		}
        	}
        	}
        }
    }
}