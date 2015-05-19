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
import java.io.File;
import java.io.FileWriter;
import java.io.*;
import org.slf4j.*;


public class zkhaproxy implements Watcher {
	private static final Logger LOG = LoggerFactory.getLogger(zkhaproxy.class);

	ZooKeeper zk;
	String hostPort;
	String s = null; // Contain output of process p.

	Path pathOrig, finalPath, pathTemp; //Paths for the original conf file and the destination.
	
	zkhaproxy(String hostPort){ //On creation the port where to connect is specified.
		this.hostPort = hostPort;
	}
	
	void startZK() throws IOException{
		zk = new ZooKeeper(hostPort, 15000, this); // We connect to the server and stablish a sesion.
	}
	
	public void process(WatchedEvent e){ // If a watcher triggers.
		if(e.getType() == EventType.NodeChildrenChanged){ //Of the type we want to recive callback from.
			assert "/web/malaria".equals(e.getPath()); // We check that the directory is the one from we want feedback.
			checkServers();
		}
	}
		
	void checkServers(){
	/* Retrieve the list of avaible servers and set a watcher under the node
	so it triggers if anything changes under it */
		zk.getChildren("/web/malaria", true, checkServersCallback, null); 
	}

	ChildrenCallback checkServersCallback = new ChildrenCallback (){ 
		public void processResult(int rc, String path, Object ctx, List<String> children){
	// If sucess the list children may have the names of all the servers.
			switch(Code.get(rc)){
				case CONNECTIONLOSS: 
					checkServers(); // retry.
					break;
				case OK: // update the conf file.
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
	/* A template of the conf file without exist with the path /etc/haproxy/haproxy.cfg.orig .
	We copy it to a new file, connect to zookeeper to get the metadata of the servers
	(its ip and port) and write it to the file. If all goes ok we rewrite the 
	principal conf file and tell haproxy to reload the data. Otherwise we just
	retry. */

		pathOrig = Paths.get("/etc/haproxy/haproxy.cfg.orig"); // Path of template for haproxy.cfg
		pathTemp = Paths.get("/etc/haproxy/haproxy.cfg.temp"); // Path for the temp file.
		finalPath =  Paths.get("/etc/haproxy/haproxy.cfg"); // Path for haproxy.cfg
		
		boolean error = false; // Variable for error cheking.
		
		Files.copy(pathOrig, pathTemp, REPLACE_EXISTING); // We copy the original file of configuration, it lacks the lines containing 
		// the information about the servers which is what we are going to append. 
		
		File confile  = new File("/etc/haproxy/haproxy.cfg.temp"); //Final path for the conf file.
		FileWriter exit = new FileWriter(confile, true);
		
		int num = 0;		
		
		for (String serv : list){
			try {
				byte[] data = zk.getData("/web/malaria/"+serv, false, null); // Retrieve the concerning information of the server in a sinchronous way si it doesn't get messy.
				
				char letter = 'A';
				letter =(char) (((int) letter) + num); //We need to increment the letter of the server on each loop.
				
				System.out.println("Escribiendo datos del servidor " + new String(data) + " en el fichero.");
				
				String toFile = "\tserver web"+letter+" "+ new String(data)  +" check\n";
				//String toFile = "\tserver web"+letter+" "+ new String(data)  +" cookie "+letter+" check\n"; // this sends the cookie and may be the cause of the persistence. 
				
				exit.write(toFile);
				
				num++;
				
			}catch (KeeperException e) {
				error = true; break;
			}catch (InterruptedException e){
				error = true; break;
			}

		}
		
		exit.close();

		if (!error){	
			Files.copy(pathTemp, finalPath, REPLACE_EXISTING);
			
			System.out.println("Changes on file finished. \n");
		
			Process p = Runtime.getRuntime().exec("service haproxy reload"); //This is where we tell HAProxy to reload its conf file.

			//We take the console exit for the last comand so we can watch if it all went ok or maybe an error happened.
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
			BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

			while((s=stdInput.readLine()) != null)
				System.out.println(s);
			while((s=stdError.readLine()) != null)
				System.out.println(s);
		} else {
			checkServers();	
		}
		
	}
		
		public static void main(String args[]) throws Exception, IOException{
			
			zkhaproxy w = new zkhaproxy("192.168.0.22:2181"); //We set the direction manually.
			
			w.startZK();
			
			w.checkServers();
			
			/* As we don't want the main program to end cause we have Watchers on
			Zookeeper that have to be processed we send it to an eternal loop */
			while(true){
				Thread.sleep(1000); 
			}
	}
}
