/** The target of this program is to be able to read from zookeeper 
what servers are up and keep the conf file of haproxy up to date */

import java.util.*;

import org.apache.zookeeper.AsyncCallback.*; 
import org.apache.zookeeper.*;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.Watcher.Event.EventType.*;
import org.apache.zookeeper.Watcher.Event.*;
import java.io.IOException;
import static java.nio.file.StandardCopyOption.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
//import java.nio.file.*;
import java.io.File;
import java.io.FileWriter;
import java.io.*;

import org.slf4j.*;


public class zkhaproxy implements Watcher {
	private static final Logger LOG = LoggerFactory.getLogger(zkhaproxy.class);

	ZooKeeper zk;
	String hostPort;
	String s = null; // Contain output of process p.
	//Random random = new Random(); // not in the example code.
	//String serverId = Integer.toHexString(random.nextInt()); // Our serverId fot the moment will be a random number.
	/*int startLine = 32; //The line in wich the servers config appears on haproxy.conf
	int numServers = 33; //How many servers we have registered on the file. */

	Path pathO, path; //Paths for the original conf file and the destination.
	
	zkhaproxy(String hostPort){
		this.hostPort = hostPort;
	}
	
	void startZK() throws IOException{
		zk = new ZooKeeper(hostPort, 15000, this); // We connect to the server and stablish a sesion.
	}
	
	public void process(WatchedEvent e){
		if(e.getType() == EventType.NodeChildrenChanged){
			assert "/web/malaria".equals(e.getPath()); // We check that the directory is the one from we want feedback.
			checkServers();
		}
	}
		
	void checkServers(){
		zk.getChildren("/web/malaria", true, checkServersCallback, null); // The web servers must be under /web/malaria. We set a watcher.
	}

	ChildrenCallback checkServersCallback = new ChildrenCallback (){ 
		public void processResult(int rc, String path, Object ctx, List<String> children){
			switch(Code.get(rc)){
				case CONNECTIONLOSS: 
					checkServers(); //In case we lose the connection we retry.
					break;
				case OK:
					try{
						updateConfFile(children); 
					} catch(IOException e) {
					}
				default:
					LOG.error("Something went wrong: " + KeeperException.create(Code.get(rc), path)); //This is the exit for the codes 
					// we are not handling.
				}
			}
		};
	
	void updateConfFile(List<String> list) throws IOException{

		pathO = Paths.get("/etc/haproxy/haproxy.cfg.orig"); // Path of template for haproxy.cfg
		path =  Paths.get("/etc/haproxy/haproxy.cfg"); // Path for haproxy.cfg
		
		Files.copy(pathO, path, REPLACE_EXISTING); // We copy the original file of configuration, it lacks the lines containing 
		// the information about the servers which is what we are going to append. 
		
		File confile  = new File("/etc/haproxy/haproxy.cfg");
		
		FileWriter exit = new FileWriter(confile, true);
		
		int num = 0;		
		
		for (String serv : list){
			try {
				byte[] data = zk.getData("/web/malaria/"+serv, false, null); // Retrieve the concerning information of the server.
				
				char letter = 'A';
				letter =(char) (((int) letter) + num);
				
				System.out.println("Escribiendo datos del servidor " + new String(data) + " en el fichero.");
				
				String toFile = "\tserver web"+letter+" "+ new String(data)  +" check\n";
				//String toFile = "\tserver web"+letter+" "+ new String(data)  +" cookie "+letter+" check\n"; // this sends the cookie and may be the cause of the persistence. 
				
				exit.write(toFile);
				
				num++;
				
			}catch (KeeperException e) { // Esperemos que nunca de este error =D
			}catch (InterruptedException e){
			}

		}
		
		System.out.println("Cambios en el fichero realizados. \n");
		exit.close();
		
		Process p = Runtime.getRuntime().exec("service haproxy reload");
		//Process p = Runtime.getRuntime().exec("haproxy -f /etc/haproxy/haproxy.cfg -p $(</var/run/haproxy-private.pid) -st $(</var/run/haproxy-private.pid)");
		//Process p = Runtime.getRuntime().exec("touch /home/pepper/workingjava ");
		//Process p = Runtime.getRuntime().exec(new String[] {"haproxy", "-f", "/etc/haproxy/haproxy.cfg", "-p", "/var/run/haproxy.pid", "-sf", "$(cat /var/run/haproxy.pid)"});
		//Process p22 = Runtime.getRuntime().exec(new String[] {"echo", "funcionando funcionando"});
		//Process p = Runtime.getRuntime().exec("/home/pepper/scriptproxy"); // Execute an external script with the propper command for haproxy. 
		//ProcessBuilder b = new ProcessBuilder("/home/pepper/scriptproxy");

		BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
		BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

		while((s=stdInput.readLine()) != null)
			System.out.println(s);

		while((s=stdError.readLine()) != null)
			System.out.println(s);
		
		
	}
		
		public static void main(String args[]) throws Exception, IOException{
			
			zkhaproxy w = new zkhaproxy("192.168.0.22:2181");
			
			w.startZK();
			
			w.checkServers();
			
			//Thread.sleep(600000); //This is not very elegant but it's the only way i known from preventing the main thread to end.
			while(true){
				Thread.sleep(1000);
			}
	}
}
