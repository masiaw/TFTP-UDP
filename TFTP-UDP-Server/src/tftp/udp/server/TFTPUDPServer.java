/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tftp.udp.server;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Random;

/**
 *
 * @author 198861
 */
public class TFTPUDPServer extends Thread{
    
    //Define max packet size
    private static final int PACKET_SIZE=516;
    //OP codes
    private static final byte RRQ=1;
    private static final byte WRQ=2;
    private static final byte DATA=3;
    private static final byte ACK=4;
    private static final byte ERROR=5;
    private static int clientPort;
    InetAddress serverIP;
    DatagramSocket clientSocket = null; //curent socket
    DatagramSocket firstSocket = null;//first socket
    Random r = new Random();
    int tid = r.nextInt(65534-1025) + 1025;
    
    
    public TFTPUDPServer() throws SocketException, IOException {
        this("TFTPServer");
    }

    public TFTPUDPServer(String name) throws SocketException, UnknownHostException, IOException {
        super(name);
        firstSocket = new DatagramSocket(10000);
    }
    
    
    //This is the body of the server and will make sure the right operations are carried out
    @Override
    public void run() {
        //waits to receive packet from client
        byte[] request = new byte[256];
        DatagramPacket packet = new DatagramPacket(request,256);
        try {// run forever           
            while (true) {               
                System.out.println("Waiting for packet");
                firstSocket.receive(packet);// holds the packet from client
                serverIP = packet.getAddress();//the address
                clientPort = packet.getPort();// the clients port
                clientSocket = new DatagramSocket(tid);//sets the socket to the new port generated randomly                
                byte[] data = packet.getData();//get data from the received packet
                byte[] opCode = {data[0], data[1]};//get the opcode
                byte zerobyte = 0;
                int value = 2;
                ByteArrayOutputStream ByteArrayOut = new ByteArrayOutputStream();
                while (data[value] != zerobyte) {
                    ByteArrayOut.write(data[value]);// retrieve the sile name string
                    value += 1;
                }
                byte[] arrayFile = ByteArrayOut.toByteArray();
                String fileName = getFileName(arrayFile);//convert byte to string
                if (opCode[1] == RRQ) {
                    System.out.println("Read Request Received");
                    readFile(fileName);// if it is a read request
                } else if (opCode[1] == WRQ) {
                    System.out.println("Write Request Received");
                    writeFile(fileName);// if it is a write request
                }

            }
        } catch (IOException e) {
            System.err.println(e);
        }
        clientSocket.close();
    }

    
   
    // Converts byte array to string form
    private String getFileName(byte[] data) {
        String s = new String(data);
        return s;
    }
    
    
    // Sends the data of the file to the client as packets and waits for ack packet to continue the transmission of the next packet
    private void readFile(String fileName) throws  IOException {
        checkForFile(fileName);
        File transferFile =  new File(fileName);
        // initialise a reader for reading from the file
        FileInputStream reader = new FileInputStream(transferFile);
        // initialise variables for while loop
        int block = 1;
        DatagramPacket ack = null;// sustains the ack packet 
        byte[] data = null;
        boolean end = false;
        while (reader.available() > 0 && !end) {
            System.out.println(reader.available());
            if (reader.available() >= 512) {// split data to correct length
                data = new byte[512];
                reader.read(data);
            } else {
                data = new byte[reader.available()];
                reader.read(data);
            }
            // make and send the next packet
            DatagramPacket packet = DataPacket(data, block);
            DatagramPacket ack2;
            ack2 = sendPacket(packet);
            if(ack.getData()[3]!= (byte)block){//check ack packet block number
                end = true;
            }
            // increment block for next packet
            block++;
        }
    }

    
    //Receives data from the client and it will write it on the file
    private void writeFile(String fileName) throws IOException {
        int block = 0;//initialise block number
        // send initial acknoledgement
        clientSocket.send(ACKPacket(block));//send the first packet
        block++;// increment block number for the next packet

        // initialise a byte array ready to collect the data from the packets
        ByteArrayOutputStream ByteArrayOut = new ByteArrayOutputStream();
        boolean end = false;
        DatagramPacket received = new DatagramPacket(new byte[PACKET_SIZE], PACKET_SIZE);
        // loop until an unfilled packet arrives
        while (!end) {
            received = ReceivedOrResent(received);// packet is received
            // check if packet has expected block number
            if (received.getData()[3]== (byte)block) {
                System.out.println(received.getLength());
                // retrieve the data 
                byte[] data = Arrays.copyOfRange(received.getData(), 4, received.getLength());
                // check if it is last packet or not 
                if (received.getData().length == PACKET_SIZE) {
                    ByteArrayOut.write(data);//write data
                } else {
                    end = true;//end while loop
                    ByteArrayOut.write(data);// write data
                }
                System.out.println("received block " + block);
                clientSocket.send(ACKPacket(block));// send ack packet
                block++;// increment block number for the next packet
            }
        }
        byte[] file = ByteArrayOut.toByteArray();
        File nfile = new File(fileName);// 
        if(!nfile.exists()){//check if the file exists
            nfile.createNewFile();//create the file
        }
        FileOutputStream fileOut = new FileOutputStream(nfile);
        fileOut.write(file);// write the data to the file created or modified
        fileOut.close();
    }

    
    //Creates Acknowledgement packet
    private DatagramPacket ACKPacket(int block) throws IOException {
        byte[] opcode = new byte[2];//set opcode length
        byte[] nblock = new byte[2];// set block number length
        opcode[1] = ACK;//establish opcode
        nblock[1] = (byte)block;// establish blick number
        ByteArrayOutputStream ByteArrayOut = new ByteArrayOutputStream();// concatenate all bytes to a single byte array
        ByteArrayOut.write(opcode);
        ByteArrayOut.write(nblock);
        byte[] arrayFile = ByteArrayOut.toByteArray();
        DatagramPacket packet = new DatagramPacket(arrayFile, arrayFile.length, serverIP, clientPort);// establish packety
        return packet;
    }

    
    //Creates error packet
    private DatagramPacket ErrorPacket() throws IOException {
        byte num = 1;// establish error code(packet not found code = 1)
        byte[] opcode = new byte[2];// set opcode length
        byte[] code = new byte[2];// set block nuber length
        opcode[1] = ERROR;// establish opcode
        code[1] = num;// establish error code
        String msg = "File not found";// establich error message
        byte [] emsg = msg.getBytes();// conver string to byte
        byte zerobyte = 0;
        ByteArrayOutputStream ByteArrayOut = new ByteArrayOutputStream();// concatenate all bytes to a single byte array
        ByteArrayOut.write(opcode);
        ByteArrayOut.write(code);
        ByteArrayOut.write(emsg);
        ByteArrayOut.write(zerobyte);
        byte[] arrayFile = ByteArrayOut.toByteArray();
        DatagramPacket packet = new DatagramPacket(arrayFile, arrayFile.length, serverIP, clientPort);// establish packet
        return packet;
    }
    
        //Creates Data packet
    private DatagramPacket DataPacket(byte[] data, int block) throws IOException {
        byte[] opcode = new byte[2];//set opcode length
        byte[] nblock = new byte[2];// set block length
        opcode[1] = DATA;//establish opcode 
        nblock[1] = (byte)block;// establish block number
        ByteArrayOutputStream ByteArrayOut = new ByteArrayOutputStream();// values into a single byte  array
        ByteArrayOut.write(opcode);
        ByteArrayOut.write(nblock);
        ByteArrayOut.write(data);
        byte[] arrayFile = ByteArrayOut.toByteArray();
        DatagramPacket packet = new DatagramPacket(arrayFile, arrayFile.length, serverIP, clientPort);// set packet 
        return packet;
    }
    
    //Sends packet to the server
    private DatagramPacket sendPacket(DatagramPacket packet) throws IOException{
        byte[] buf = new byte[PACKET_SIZE];
        DatagramPacket received = new DatagramPacket (buf, PACKET_SIZE);// packet that will hold new packet
        clientSocket.setSoTimeout(10000);// set timer
        clientSocket.send(packet);  // send packet
        received = ReceivedOrResent(packet);// set the packet received 
        return received;
    }
    
    
   //This methods waits to receive packet for server, if timer runs out it will resent last packet
    private DatagramPacket ReceivedOrResent(DatagramPacket packet) throws IOException{
        byte[] buf = new byte[PACKET_SIZE];
        DatagramPacket received = new DatagramPacket (buf, PACKET_SIZE);//set packet to sustain the new packet
        try {
            clientSocket.receive(received);
            String receivedP = "Packet received from " + received.getAddress() + ", " + received.getPort();
            clientPort = received.getPort();
            System.out.println(receivedP);//packet is received and new port is set            
            }
            catch (SocketTimeoutException e) {// timeout exception.
                System.out.println("Timeout reached!" + e);
                sendPacket(packet);// server's packet is not received so the client send packet again to the server
        }
        return received;
    }
    
    //Checks if file exists, if not sends error packet indicating the file could not be found
    private void checkForFile(String s) throws IOException{
        File f = new File(s);
        //Check if file exists
        if(!f.exists()){
            System.out.println(System.getProperty("user.dir") + "/" + s + " does not exist");
            clientSocket.send(ErrorPacket());//send error packet indicating the file could not be found
        }
    }

    
    // main methos stating the server is running
    public static void main(String[] args) throws IOException {
        new TFTPUDPServer(args[0]).start();
        System.out.println("Server Started");     
       
    }

}
