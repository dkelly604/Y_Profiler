import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

/*
 * Author David Kelly
 * 
 * Date 08/08/2019
 * 
 * Companion script for the Y_Profile_Maker plugin. Its
 * a standalone java program to read the ProfileResults text
 * file output from the Y_Profile_Maker plugin. The purpose 
 * is to quickly pick out the distances, and intensities 
 * for the red and green dots.
 */

public class Y_ProfileTextPeaks {
		
	private static File DirLocText;
	static String file;
	static StringBuilder FinalStringBuilder;
	static int[] GreenIntensities;
	static int[] RedIntensities;
	static String filename;
	static boolean firstRun;
	
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					StartHere();
				
				} catch (Exception e) {
					e.printStackTrace();
				} catch (Throwable e) {
					
					e.printStackTrace();
				}
			}
		});
	}
	
	public static void StartHere() throws Throwable {
		JOptionPane.showMessageDialog(null, "Open Text File.");
		DirLocText = FindTextDirectory();
	
		readTextfile();
		JOptionPane.showMessageDialog(null, "All Done.");
	}
	
	public static File FindTextDirectory(){
		
		File DirLocText = null;
		JFileChooser j = new JFileChooser();
		j.setCurrentDirectory(new java.io.File("."));
		j.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		int returnVal = j.showOpenDialog(null);
		if(returnVal == JFileChooser.APPROVE_OPTION) {
		    DirLocText = j.getSelectedFile();
		}
		return DirLocText;
	}
	
	public static void readTextfile() throws IOException{
		
		String filepos = DirLocText.getAbsolutePath();
		filename = filepos.substring(filepos.lastIndexOf("\\") + 1).trim();
		firstRun = true;
		file = filepos;
		BufferedReader reader = new BufferedReader( new FileReader (file));
		String line = null;
		//StringBuilder  stringBuilder = new StringBuilder();
	
		FinalStringBuilder= new StringBuilder();
		while( ( line = reader.readLine() ) != null ) {
			
			Map<String,List<Integer>> peaksTroughs=new HashMap<String,List<Integer>>();
			String [] greenVals=null;
			String [] redVals;
			
			if (!(line.contains("Distance = 0.0"))&&line.length()>0){
				// Get rid of text
				String [] splitText = line.split("=");
				String theVals = splitText[2];
				
				if(line.contains("GreenProfile")){
					greenVals = theVals.split(","); 
					
					//Green intensity Values
					int[] intGreenVals = new int [greenVals.length];
					for (int y =0;y<greenVals.length;y++){
						String temp = greenVals[y];
						greenVals[y] = temp.trim();
						intGreenVals[y]=Integer.parseInt(greenVals[y]);
					}
					
					if(GreenIntensities!=null){
						GreenIntensities=null;
					}
					GreenIntensities=new int[intGreenVals.length];
					for(int a=0;a<intGreenVals.length;a++){
						GreenIntensities[a] = intGreenVals[a];
					}
				}
				
				
				if(line.contains("RedProfile")){
					redVals = theVals.split(",");
					peaksTroughs =	peakfind(redVals);
				}
			}
			if(peaksTroughs.isEmpty()==false){
				findTwoBiggestPeaks(peaksTroughs);
				
				
			}
		}
		reader.close();
	
	
	}

	public static Map<String,List<Integer>> peakfind(String[] redVals){
		
		//Convert String vals to integer vals
		//Red intensity Values 
		int[] intRedVals = new int [redVals.length];
		for (int y =0;y<redVals.length;y++){
			String temp = redVals[y];
			redVals[y] = temp.trim();
			intRedVals[y]=Integer.parseInt(redVals[y]);
		}
		
		if(RedIntensities!=null){
			RedIntensities=null;
		}
		RedIntensities=new int[intRedVals.length];
		for(int a=0;a<intRedVals.length;a++){
			RedIntensities[a]=intRedVals[a];
		}
		
		
		
		List<Integer> pos=new ArrayList<>();    
		List<Integer> pea=new ArrayList<>();
		Map<String,List<Integer>> peaksTroughs=new HashMap<String,List<Integer>>();
		int cur=0,pre=0;
		   for(int a=1;a<intRedVals.length;a++){
		    
			if(intRedVals[a]>intRedVals[cur] ){
		     pre=cur;cur=a;
		     }else{
		     if(intRedVals[a]<intRedVals[cur])
		      if(intRedVals[pre]<intRedVals[cur]){
		     pos.add(cur);pea.add(intRedVals[cur]);}
		     pre=cur;cur=a;
		     }

		   }
		   peaksTroughs.put("pos",pos);
		   peaksTroughs.put("peaks",pea);
		
		  
		return peaksTroughs;
	
	}
	
	public static void findTwoBiggestPeaks(Map<String,List<Integer>>peaksTroughs){
		
		int biggestPeak = 0 ;
		int nextBiggestPeak = 0;
		int biggestPosition = 0;
		int nextbiggestPosition = 0;
		
		//Get the number of peaks found
		int numMapVals=0;
		for ( Map.Entry<String, List<Integer>> entry : peaksTroughs.entrySet()) {
			List<Integer> Value = entry.getValue();
			numMapVals =Value.size();
		}
		int thePositions[] = new int[numMapVals];
		int theIntensities[] = new int[numMapVals];
		
		//Extract the key and value pairs
		for ( Map.Entry<String, List<Integer>> entry : peaksTroughs.entrySet()) {
		    String Key = entry.getKey();
		    List<Integer> Value = entry.getValue();
		
		    if(Key.equals("pos")){
		    	int count = 0;
		    	for(Integer item:Value){
		    		thePositions[count]=item;
				   count++;
		    	}
		    }
		    
		    if(Key.equals("peaks")){
			   int count = 0;
			   for(Integer item:Value){
				   theIntensities[count]=item;
				   count++;
		    	}
		    }
		}
		
		double distanceVal=0;
		if (numMapVals == 2){
			biggestPeak=theIntensities[0];
			biggestPosition=thePositions[0];
			nextBiggestPeak=theIntensities[1];
	    	nextbiggestPosition=thePositions[1];
	    	//Calculate distance
			int numPixels = Math.abs(biggestPosition-nextbiggestPosition);
			distanceVal = (numPixels+1)*0.0645;
		}
		else{
			for(int x=0;x<numMapVals;x++){
		   
				if(theIntensities[x]>biggestPeak){
					nextBiggestPeak=biggestPeak;
					nextbiggestPosition=biggestPosition;
					biggestPeak=theIntensities[x];
					biggestPosition=thePositions[x];
				
				}
				if(theIntensities[x]<biggestPeak && theIntensities[x]>nextBiggestPeak ){
					nextBiggestPeak=theIntensities[x];
					nextbiggestPosition=thePositions[x];
				}
		}
		
			int RedPeakOne = biggestPeak;
			int RedPeakTwo = nextBiggestPeak;
			//Find Middle Position
			int middlePos=0;
			if(biggestPosition>nextbiggestPosition){
				int tempmiddlePos = Math.abs(biggestPosition-nextbiggestPosition);
				middlePos = Math.abs(biggestPosition-(tempmiddlePos/2));
			}
			if(biggestPosition<nextbiggestPosition){
				int tempmiddlePos = Math.abs(biggestPosition-nextbiggestPosition);
				middlePos = Math.abs(nextbiggestPosition-(tempmiddlePos/2));
			}
			int GreenMiddleVal = GreenIntensities[middlePos];
			int GreenPeakOne = GreenIntensities[biggestPosition];
			int GreenPeakTwo = GreenIntensities[nextbiggestPosition];
			//Calculate distance
			int numPixels = Math.abs(biggestPosition-nextbiggestPosition);
			distanceVal = (numPixels+1)*0.0645;
			OutputText(RedPeakOne,RedPeakTwo,GreenPeakOne,GreenPeakTwo,distanceVal,GreenMiddleVal);
		}
		
		
	}

	public static void OutputText(int RedPeakOne,int RedPeakTwo,int GreenPeakOne,int GreenPeakTwo,double distanceVal,int GreenMiddleVal){
		String CreateName = "C:/Temp/Results.txt";
		String FILE_NAME = CreateName;
		
		
		
		//Average the Green Intensities
    	double greenAv = (GreenPeakOne + GreenPeakTwo)/2;

		
		try{
			FileWriter fileWriter = new FileWriter(FILE_NAME,true);
			BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
			if(firstRun==true){
				bufferedWriter.newLine();
				bufferedWriter.write("FileName = " + filename);
				bufferedWriter.newLine();
				firstRun = false;
			}
			bufferedWriter.newLine();
			bufferedWriter.write("Distance = " + distanceVal + " Green Mean Intensity = " + greenAv + " Green Middle Value = " + GreenMiddleVal + " Green Intensity 1 = " + GreenPeakOne + " Green Intensity 2 = " + GreenPeakTwo + " Red Intensity 1 = " + RedPeakOne + " Red Intensity 2 = " + RedPeakTwo);
			bufferedWriter.newLine();
	
		
		
			bufferedWriter.close();

		}
		catch(IOException ex) {
			System.out.println("Error writing to file '" + FILE_NAME + "'");
    }
	}
}
