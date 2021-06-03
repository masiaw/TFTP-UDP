/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tftp.udp.client;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.Scanner;

/**
 *
 * @author 198861
 */
public class TFTPUDPClient {
    
    private static final int port = 12345;
    private static final String MODE = "octet";
    //OP codes
    private static final byte RRQ=1;
    private static final byte WRQ=2;
    private static final byte DATA=3;
    private static final byte ACK=4;
    private static final byte ERROR=5;
    //Define max packet size
    private static final int PACKET_SIZE=516;
    private static int clientPort;
    InetAddress serverIP;
    DatagramSocket clientSocket;
    private String fileName;
    private short blockNum; //current block number
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws UnknownHostException, SocketException, IOException {
        //No arguments so no address
        if (args.length != 1) {
            System.out.println("the hostname of the server is required");
            return;
        }
        TFTPUDPClient client = new TFTPUDPClient();
        client.createSocket(args[0]);
        client.menuInput();                
    }
    
    
    //Menu that allows user to choose whether they'd like to read or write a file
    private void menuInput() throws UnknownHostException, SocketException, IOException{
        System.out.println("OPTIONS\n=======\n1. Read a file \n2. Write a file");
        Scanner reader = new Scanner(System.in);
        int input = reader.nextInt();
        System.out.println("Enter file name:");
        //Scan user input
        Scanner filetoreadwrite = new Scanner(System.in);
        fileName = filetoreadwrite.nextLine();
        checkForFile(fileName);
        DatagramPacket packet;
        switch(input){
            case 1:  
                //Create packet with filename based on user input
                packet = new DatagramPacket(createRequest(RRQ,fileName, MODE), createRequest(RRQ,fileName, MODE).length,serverIP,port);
                clientSocket.setSoTimeout(10000); //Start timeout timer
                clientSocket.send(packet); //Send request packet to server
                receivedData(packet); //Wait for acknowledgement from server
                break;
            case 2:
                //Create packet with filename based on user input
                packet = new DatagramPacket(createRequest(WRQ,fileName, MODE), createRequest(WRQ, fileName, MODE).length,serverIP,port);
                clientSocket.setSoTimeout(10000); //Start timeout timer
                clientSocket.send(packet); //Send request packet to server
                if (receivedAck(packet)){
                    writeFile();//if received write a file
                }   
                break;
            default: System.out.println("Invalid Input");
                menuInput();
        }  
    }
    
    
    //Method to create read requests and write requests
    private byte[] createRequest(byte opCode, String filename, String mode)throws IOException{
        byte zero = 0;
        int rqLength = 2 + filename.length() + 1 + mode.length() + 1; //Define length of byte array.
        byte[] rqByteArray = new byte[rqLength];
        int i = 0;
        rqByteArray[i] = zero;
        i++;
        rqByteArray[i] = opCode;
        i++;
        for (int j=0;j<filename.length();j++){
            rqByteArray[i]=(byte) filename.charAt(j);
            i++;
        }
        rqByteArray[i] = zero;
        i++;
        for (int j=0;j<mode.length();j++){
            rqByteArray[i] = (byte) mode.charAt(j);
            i++;
        }
        rqByteArray[i] = zero;
        return rqByteArray;
    }
    
    //Generate a random number for the port
    //Assigns the InetAddress object of the server IP address
    //Creates the datagram socket for the client.
    private void createSocket(String s) throws UnknownHostException, SocketException{
        Random r = new Random();
        int min = 1025;
        int max = 65535;
        int result = r.nextInt(max-min) + min;
        clientPort = result;
        serverIP = InetAddress.getByName(s);
        clientSocket = new DatagramSocket(clientPort, serverIP);//create a socket
    }
    
    //Creates new ack packet
    private void sendAck(byte[] blockNumber, InetAddress address, DatagramPacket packet, DatagramSocket socket) throws IOException{
        byte[] ackByteArray = {0, ACK, blockNumber[0], blockNumber[1]};
        DatagramPacket ack = new DatagramPacket(ackByteArray, ackByteArray.length, serverIP, clientPort);
        socket.send(ack);
    }
    
    //Writes in a file and sends it to the server
    private void writeFile() throws IOException {
        DatagramPacket packet;// create packet
        DatagramPacket received;
        fileWriter(fileName);// This will input data to the file
        File nfile = new File(fileName);
        FileInputStream reader = new FileInputStream(nfile);// read the file
        int block = 1;
        byte[] data2 = null;
        // Read the new data in the file and splitit into packet size sections and send it to the server
        while (reader.available() > 0) {
            System.out.println(reader.available());
            if (reader.available() >= 512) {
                data2 = new byte[512];
                reader.read(data2);
            } else {
                data2 = new byte[reader.available()];
                reader.read(data2);
            }            
            // make the next packet and send
            packet = DataPacket(data2,block);
            packet.setPort(clientPort);
            received = sendPacket(packet);// send packet and received servers response packet
            // increment block ready for next packet
            block++;
        }
    }
    
    
    //Sends packet to the server
    private DatagramPacket sendPacket(DatagramPacket packet) throws IOException{
        byte[] buf = new byte[PACKET_SIZE];
        DatagramPacket received = new DatagramPacket (buf, PACKET_SIZE);// packet that will hold new packet
        clientSocket.setSoTimeout(10000);// set timer
        clientSocket.send(packet);  // send packet
        received = ReceivedOrResent(packet);// set the packet received 
        return received;// return the packet received
    }
    
    
    //This methods waits to receive packet for server, if timer runs out it will resend last packet
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
                sendPacket(packet);// server's packet is not received so theclient send packet again to the server
        }
        return received;
    }

    
    // Writes the file in the client sides and sends ack packet to server if data arrives
    private void receivedData(DatagramPacket packet) throws IOException {
        boolean stop = false;
        byte[] buf = new byte[PACKET_SIZE];
        DatagramPacket received = new DatagramPacket (buf, PACKET_SIZE);// set packet to receive packet from server
        ByteArrayOutputStream ByteArrayOut = new ByteArrayOutputStream();
        int block = 1;
        received = ReceivedOrResent(packet);// receives server's packet
        byte [] opCode = received.getData();
            if(opCode[1] != ERROR){// check it isn't an error packet
                while (!stop){
                    byte nblock = (byte)block;
                    byte[] data = received.getData();
                    byte[] blockNumber = { data[2], data[3] };
                    if (blockNumber[1] == nblock) {
                        ByteArrayOut.write(data, 4,received.getLength() - 4);// retrieve file data
                        sendAck(blockNumber,serverIP,packet, clientSocket);// send ack to receive the next packet	
                    }
                    System.out.println("TFTP Packet count: " + block);
                    received = ReceivedOrResent(packet);
                    block++;
                    if(data.length < 512){
                        stop = true;
                        ByteArrayOut.write(data, 4,received.getLength() - 4);// retrieve file data
                    }
                }
                FileOutputStream fileOut = new FileOutputStream(fileName);
                byte[] file = ByteArrayOut.toByteArray();
                fileOut.write(file);// write data inside the file on client sides
                fileOut.close();
            }else{System.err.println("File not found");}
    }

  
    // Receives acknowledment to indicate connection is established and user able to write 
    private boolean receivedAck(DatagramPacket packet) throws IOException {
        byte[] buf = new byte[4];
        DatagramPacket received = new DatagramPacket (buf, 4);// establish ack packet
        received = ReceivedOrResent(packet);
        byte nblock = (byte) blockNum;//get block number
        byte[] data = received.getData();
        byte[] blockNumber = { data[2], data[3] };
        byte [] opCode = received.getData();
        System.out.println(opCode[3]);
        if(opCode[1] != ERROR){//check the received packet is not an eroor packet
            if (blockNumber[1] == nblock) {
                blockNum++;// establish next block number
                return true;	
            }else{
                return false;
            }
        }  
        else{
            return false;
        }
    }

    
    //Writes data in the file
    private void fileWriter(String file) throws IOException {
        System.out.println("Enter data:");// ask user to implement data
        Scanner myObj = new Scanner(System.in);
        String info = myObj.nextLine();
        FileWriter writer = new FileWriter(file);
        writer.write(info);// writes new data into the file
        writer.close();// close data
        System.out.println("Done");// let's user know it's finished
    }
    
    //Checks if file exists, if not asks for another file
    private void checkForFile(String s) throws IOException{
        File f = new File(s);
        //Check if file exists
        if(f.exists()){
            System.out.println(System.getProperty("user.dir") + "/" + s + " exists");
            fileName = s;
        }
        //If not client must enter a valid filename.
        else {
            System.out.println(System.getProperty("user.dir") + "/" + s + " does not exist");
            System.out.println("Enter file name to write: ");
            Scanner reader = new Scanner(System.in);
            checkForFile(reader.nextLine());
        }
    }
    
    
    //Creates data packet
     private DatagramPacket DataPacket(byte[] data, int block) throws IOException {
        byte[] opcode = new byte[2];//state two bytes for opcode
        byte[] nblock = new byte[2];//state two bytes for block
        nblock[1] = (byte)block;// establish block number
        opcode[1] = DATA;// establish data opcode
        ByteArrayOutputStream ByteArrayOut = new ByteArrayOutputStream();// concatenate all values 
        ByteArrayOut.write(opcode);
        ByteArrayOut.write(nblock);
        ByteArrayOut.write(data);
        byte[] arrayFile = ByteArrayOut.toByteArray();
        DatagramPacket packet = new DatagramPacket(arrayFile, arrayFile.length, serverIP, clientPort);// create packet and use new port
        return packet;
    }
    
    
}
    

