import java.awt.Color;
import java.awt.Frame;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;
import java.util.TreeMap;
import javax.swing.JOptionPane;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.gui.NewImage;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.WaitForUserDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.MaximumFinder;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import ij.util.ArrayUtil;
import ij.util.Tools;

/*
 * Author: D Kelly
 * 
 * Date: 03/05/19
 * 
 * Plugin to take user added line profiles on the red
 * channel and apply them to the green channel. The 
 * profiles are ordered in terms of the distance between
 * the 2 red dots in the image to make another image
 * where the brightest points (red dots) fan out to produce 
 * a Y shape. All distances between the dots are calculated
 * and output to a text file. The peaks coressponding to the 
 * dots are found using the peakfinder script from with the BAR
 * plugin written by Tiago Ferreira (2016) DOI:10.5281/zenodo.28838
 */

public class Y_Profile_Maker implements PlugIn{

	//BAR PeakFinder global variables
	double tolerance = 0d;
	double minPeakDistance =0d;
	double minMaximaValue = Double.NaN;
	double maxMinimaValue = Double.NaN;
	boolean excludeOnEdges = false;
	boolean listValues = false;	
	double[] xvalues;
	double[] yvalues;
	
	//Y_Profiler global variables	
	String filename;
	ImagePlus GreenImage;
	ImagePlus RedImage;
	int RedId;
	int GreenId;
	double[] greenDistance = new double[500];
	double[] redDistance = new double [500];
	double[] GreenIntensity = new double[500];
	double[] RedIntensity = new double[500];
	Object[] GreenY = new Object[500];
	Object[] RedY = new Object[500];
	double[] RedMiddleDistanceValue = new double [500];
	double[] GreenMiddleDistanceValue = new double [500];
	
	
	public void run(String arg) {
		

		new WaitForUserDialog("Open Image", "Open Image. SPLIT CHANNELS!").show();
		IJ.run("Bio-Formats Importer");
		
		//Get Filename for output
		ImagePlus imp = WindowManager.getCurrentImage();
		filename = imp.getShortTitle(); 	//Get file name
		
		ProcessAndIdentify();
		String theanswer = "y";
		int Counter = 0;
		
		do {
			ClearROI();
			IJ.selectWindow(RedId);
			IJ.setTool("line");
			new WaitForUserDialog("Plot", "Draw line across dots").show();
			IJ.run(RedImage, "Plot Profile", "");
			
			int slicepos = RedImage.getCurrentSlice();
			
			//Get Line ROI to pass to green image
			IJ.run("ROI Manager...", "");
			RoiManager rm = new RoiManager();    
			rm = RoiManager.getInstance();
			rm.addRoi(RedImage.getRoi());
			
			//peakfind in red
			String colour = "r";
			peakfind();
			ExtractResults(colour,Counter);
			ClosePlot();
	
			//Add ROI Line to green image
			IJ.selectWindow(GreenId);
			GreenImage.setSlice(slicepos);
			rm.select(0);	
			IJ.run(GreenImage, "Plot Profile", "");
			
			//peakfind in green
			colour = "g";
			peakfind();
			ExtractResults(colour,Counter);
			ClosePlot();
			rm.runCommand(imp,"Deselect");
			rm.runCommand(imp,"Delete");
			
			Counter++;
			theanswer = JOptionPane.showInputDialog("Do Another? y/n","y");
		}while(theanswer.equals("y"));
		
		SortArrays(Counter);
		
		new WaitForUserDialog("Finished", "Plugin Finished").show();

	}

	public void ProcessAndIdentify(){
		
		/*
		 * Identify and Z Project the green channel image
		 */
		new WaitForUserDialog("Select Green Image", "Click on the green image and then OK").show();
		ImagePlus tempGreenImage = WindowManager.getCurrentImage();
		IJ.run(tempGreenImage, "Z Project...", "projection=[Max Intensity] all");
	    GreenImage = WindowManager.getCurrentImage();
	    GreenId = GreenImage.getID();
	    IJ.run(GreenImage, "Enhance Contrast", "saturated=0.35");
	    tempGreenImage.changes = false;
	    tempGreenImage.close();
	    
	    /*
		 * Identify and Z Project the red channel image
		 */
		new WaitForUserDialog("Select Red Image", "Click on the red image and then OK").show();
		ImagePlus tempRedImage = WindowManager.getCurrentImage();
		IJ.run(tempRedImage, "Z Project...", "projection=[Max Intensity] all");
	    RedImage = WindowManager.getCurrentImage();
	    RedId = RedImage.getID();
	    IJ.run(RedImage, "Enhance Contrast", "saturated=0.35");
	    tempRedImage.changes = false;
	    tempRedImage.close();
	    
	    /*
		 * Identify and close DIC channel image
		 */
		new WaitForUserDialog("Select DIC Image", "Click on the DIC image and then OK").show();
		ImagePlus tempImage = WindowManager.getCurrentImage();
		tempImage.changes=false;
	    tempImage.close();
	    
	}
	
	//FUNCTION TAKEN FROM THE BAR plugin for FIJI
	public void peakfind(){
			PlotWindow pw;

			ImagePlus imp = WindowManager.getCurrentImage();   //Get Plot Window
			if (imp==null)
				{ IJ.error("There are no plots open."); return; }    //Check for open line profile plots
			ImageWindow win = imp.getWindow();
			if (win!=null && (win instanceof PlotWindow)) {
				pw = (PlotWindow)win;
				float[] fyvalues = pw.getYValues();			//Get Y values from plot window
				ArrayUtil stats = new ArrayUtil(fyvalues);
				tolerance = Math.sqrt(stats.getVariance()); //Calculate variability of points
				yvalues = Tools.toDouble(fyvalues);
				xvalues = Tools.toDouble(pw.getXValues());  //Get X values from plot window
			} else {
				IJ.error(imp.getTitle() +" is not a plot window.");
				return;
			}

		
			excludeOnEdges = true;			//Ignore the edges of the plot window
			listValues = false;		
			
			int[] maxima = findMaxima(yvalues, tolerance);
			int[] minima = findMinima(yvalues, tolerance);
			if (!Double.isNaN(minMaximaValue))
				maxima = trimPeakHeight(maxima, false);
			if (!Double.isNaN(maxMinimaValue))
				minima = trimPeakHeight(minima, true);
			if (minPeakDistance>0) {
				maxima = trimPeakDistance(maxima);
				minima = trimPeakDistance(minima);
			}
			double[] xMaxima = getCoordinates(xvalues, maxima);
			double[] yMaxima = getCoordinates(yvalues, maxima);
			double[] xMinima = getCoordinates(xvalues, minima);
			double[] yMinima = getCoordinates(yvalues, minima);

			String plotTitle = imp.getTitle();
			Plot plot = new Plot("Peaks in "+ plotTitle, "", "", xvalues, yvalues);
			plot.setLineWidth(2);
			plot.setColor(Color.RED);
			plot.addPoints(xMaxima, yMaxima, Plot.CIRCLE);
			plot.addLabel(0.00, 0, maxima.length +" maxima");
			plot.setColor(Color.BLUE);
			plot.addPoints(xMinima, yMinima, Plot.CIRCLE);
			plot.addLabel(0.25, 0, minima.length +" minima");
			plot.setColor(Color.BLACK);
			plot.addLabel(0.50, 0, "Min. amp.: "+ IJ.d2s(tolerance,2) +"  Min. dx.: "+ IJ.d2s(minPeakDistance,2) );
			plot.setLineWidth(1);

			if (plotTitle.startsWith("Peaks in"))
				pw.drawPlot(plot);
			else
				pw = plot.show();
			//if (listValues)
			if (listValues==false)
				pw.getResultsTable().show("Plot Values");
		}
		
		int[] findPositions(double[] values, double tolerance, boolean minima) {
			int[] positions = null;
			if (minima)
				positions = MaximumFinder.findMinima(values, tolerance, excludeOnEdges);
			else
				positions = MaximumFinder.findMaxima(values, tolerance, excludeOnEdges);
			return positions;
		}

		int[] findMaxima(double[] values, double tolerance) {        
			return findPositions(values, (tolerance/2), false);			//Find  peak values
		}

		int[] findMinima(double[] values, double tolerance) {
			return findPositions(values, tolerance, true);
		}

		double[] getCoordinates(double[] values, int[] positions) {
			int size = positions.length;
			double[] cc = new double[size];
			for (int i=0; i<size; i++)
				cc[i] = values[ positions[i] ];
			return cc;
		}

		int[] trimPeakHeight(int[] positions, boolean minima) {
			int size1 = positions.length; int size2 = 0;
			for (int i=0; i<size1; i++) {
				if ( filteredHeight(yvalues[positions[i]], minima) )
					size2++;
				else
					break; // positions are sorted by amplitude
			}
			int[] newpositions = new int[size2];
			for (int i=0; i<size2; i++)
				newpositions[i] = positions[i];
			return newpositions;
		}

		boolean filteredHeight(double height, boolean minima) {
			if (minima)
				return (height < maxMinimaValue);
			else
				return (height > minMaximaValue);
		}

		int[] trimPeakDistance(int[] positions) {
			int size = positions.length;
			int[] temp = new int[size];
			int newsize = 0;
			for (int i=size-1; i>=0; i--) {
				int pos1 = positions[i];
				boolean trim = false;
		 		for (int j=i-1; j>=0; j--) {
		 			int pos2 = positions[j];
		 			if (Math.abs(xvalues[pos2] - xvalues[pos1]) < minPeakDistance)
		 				{ trim = true; break; }
		 		}
		 		if (!trim) temp[newsize++] = pos1;
			}
			int[] newpositions = new int[newsize];
			for (int i=0; i<newsize; i++)
				newpositions[i] = temp[i];
			return newpositions;
		}

		private void ExtractResults(String colour, int Counter){
			Frame frame = WindowManager.getFrame("Plot Values");
			float[] peakvals = null;
			float[] distvals = null;
			float[] Alldistvals = null;
			int numpeaks = 0;
		
		    if (frame!=null && (frame instanceof TextWindow)) {
		        TextWindow tw = (TextWindow)frame;
		        ResultsTable table = tw.getTextPanel().getResultsTable();
		        if (table!= null) {
		        	//GetPeaks
		        	peakvals = table.getColumn(3);
		        	distvals = table.getColumn(2);
		        	Alldistvals = table.getColumn(0);
		        	numpeaks = 0;
		        	
		        	//Get number of peaks found
		    
		        	int totalentries = peakvals.length;
		        	for(int x=0;x<totalentries;x++){
		        		double peakcount = peakvals[x];
		        		if(peakcount>0)
		        			numpeaks++;
		        	}
		        	
		        	/*
		        	 * Find largest and 2nd largest
		        	 * peaks in plot. just goes through 
		        	 * the motions if there are only
		        	 * 2 peaks but its useful if more than 
		        	 * 2 are found.
		        	 */
		        	double distval = 0;
		        	double biggestval = 0;
		        	double midvaldistance = 0;
		        	double midPointVal = 0;
		        	if (numpeaks>1){
		        		//biggest value and its position in the list
		        		double bigval=0;
		        		double smallerval=0;
		        		int [] positions = new int[2];
		        		//Largest peak value
		        		for (int b=0;b<numpeaks;b++){
		        			if (peakvals[b]>bigval){
		        				bigval = peakvals[b];
		        				positions[0]=b;
		        				biggestval = bigval;
		        			}
		        		}
		        		//Second largest peak value
		        		for (int b=0;b<numpeaks;b++){
		        			if (peakvals[b]<bigval && peakvals[b]>smallerval){
		        				smallerval= peakvals[b];
		        				positions[1]=b;
		        			}
		        		}
		        		
		        		distval = Math.abs(distvals[positions[0]]-distvals[positions[1]]);
		        		midvaldistance = (distvals[positions[0]] + distvals[positions[1]])/2;
		        		/*
		        		 * Find middle point for aligment
		        		 */
		        		int numdistanceVals=Alldistvals.length;
		        		
		        		for (int t=0;t<numdistanceVals;t++){
		        			if (Alldistvals[t]>midvaldistance){
		        				midPointVal = t;
		        				t=Alldistvals.length;//Ends the loop as the midpoint has been found
		        			}
		        		}
		        		
		        		
		        		
		        	}else
		        	{
		        		distval = 0;
		        		biggestval = peakvals[0];
		        	}
		        	if (colour.equals("g")){
		        		greenDistance[Counter]=distval;
		        		GreenIntensity[Counter]=biggestval;
		        		GreenY[Counter]=yvalues;					//Line Profile values for green stored as object
		        		//Correct for non-divergent dots
	        			if(midvaldistance==0){
	        				midPointVal = Alldistvals.length/2;
	        			}
		        		GreenMiddleDistanceValue[Counter]=midPointVal;
		        	}
		        	if (colour.equals("r")){
		        		redDistance[Counter]=distval;
		        		RedIntensity[Counter]=biggestval;
		        		RedY[Counter]=yvalues;						//Line Profile values for Red stored as object
		        		//Correct for non-divergent dots
	        			if(midvaldistance==0){
	        				midPointVal = Alldistvals.length/2;
	        			}
		        		RedMiddleDistanceValue[Counter]=midPointVal; //middle value between the distances so that profiles can be aligned on centre trough		
		        	} 		        
		        }
		    }
		}
		
		public void ClosePlot(){
			ImagePlus imp = WindowManager.getCurrentImage();   //Get Plot Window
			imp.close();									//Close Plot Window
		}
		
		public void ClearROI(){
			ImagePlus imp=null;
			RoiManager rm = new RoiManager();    
			rm = RoiManager.getInstance();
			int numroi = rm.getCount();
			if (numroi>0){
				rm.runCommand(imp,"Deselect");
				rm.runCommand(imp,"Delete");
			}
		}
		
		public void SortArrays(int Counter){
			/*
			 * Put green and red distances into
			 * a java treemap. Use the red distances 
			 * as the key and the treemap will 
			 * automatically sort them into order
			 */
	
			TreeMap<Double,Double> SortedDistance = new TreeMap<Double,Double>();
			TreeMap<Double,Double> SortedGreenIntensity = new TreeMap<Double,Double>();
			TreeMap<Double,Double> SortedRedIntensity = new TreeMap<Double,Double>();
			TreeMap<Double,Object> SortedRedYPlotValues = new TreeMap<Double,Object>();
			TreeMap<Double,Object> SortedGreenYPlotValues = new TreeMap<Double,Object>();
			TreeMap<Double,Double> SortedRedMiddleValues = new TreeMap<Double,Double>();
			TreeMap<Double,Double> SortedGreenMiddleValues = new TreeMap<Double,Double>();
			/*
			 * Scale up distances by a factor
			 * of 10 to make the resulting 
			 * image easier to interpret 
			 */
			double [] ScaledredDistance = new double[Counter];
			double [] ScaledgreenDistance = new double[Counter];
			
			/*
			 * Loop to scale up the distances so that the plot image
			 * looks better
			 */
			for (int z=0;z<Counter;z++){
				ScaledredDistance[z]=redDistance[z]*10;
				ScaledgreenDistance[z]=greenDistance[z]*10;
			}
			
			/*
			 * Loop to populate the Treemaps for distance
			 * and intensity with the red distance as the
			 * key for all of them so that they are sorted
			 * into the same order. 
			 */
			for (int y=0;y<Counter;y++){
				SortedDistance.put(ScaledredDistance[y],ScaledgreenDistance[y]);
				SortedRedIntensity.put(ScaledredDistance[y],RedIntensity[y]);
				SortedGreenIntensity.put(ScaledredDistance[y],GreenIntensity[y]);
			}
			
			/*
			 * Loop to populate treemap with the plot intensities
			 * of the line profile used to generate the distance
			 * the red distance is used as the key so that everything 
			 * is sorted by distance between red spots. These 2 treemaps
			 * will be used to make kymograph images
			 */
			
			for (int x=0; x<Counter;x++){
				
				SortedRedYPlotValues.put(ScaledredDistance[x], RedY[x]);
				SortedGreenYPlotValues.put(ScaledredDistance[x], GreenY[x]);
				
			}
			
			/*
			 * Loop to populate treemap with the midpoint values (the trough
			 * between the 2 distance points) the midpoints are sorted with 
			 * the distance between the red spots so that they are in the 
			 * same order as all the other treemaps
			 */
			
			for(int w=0;w<Counter;w++){
				SortedRedMiddleValues.put(ScaledredDistance[w],RedMiddleDistanceValue[w]);
				SortedGreenMiddleValues.put(ScaledredDistance[w],GreenMiddleDistanceValue[w]);
			}
			
			MakeImage(SortedDistance, Counter);
			MakeKymograph(ScaledredDistance, SortedRedYPlotValues,SortedGreenYPlotValues,SortedRedMiddleValues,SortedGreenMiddleValues,Counter);
			MakePlotData(SortedRedYPlotValues,SortedGreenYPlotValues,SortedRedMiddleValues,SortedGreenMiddleValues,Counter);
			OutputText(Counter,SortedRedIntensity,SortedGreenIntensity,SortedDistance);
			OutputProfiles(Counter,SortedRedYPlotValues,SortedGreenYPlotValues);
		}
		
		public void MakeImage(TreeMap<Double, Double> SortedDistance, int Counter){
			ImagePlus newRedIm = null;
			ImagePlus newGreenIm = null;
			int bitDepth = 8;
			int slicesval = 1;
			int plotheight = Counter*5;
			int numkeyval = SortedDistance.size();

			Set<Double>theKeys=SortedDistance.keySet();
			int [] distvalred = new int[numkeyval];
			int [] distvalgreen = new int[numkeyval];
			int count = 0;
			for(Double key: theKeys){
	            double distred = key;
	            distvalred[count] = (int) distred;
	            Object distgreenobj = SortedDistance.get(distred);
	            Double d = null;
	            d = (Double) distgreenobj;          
	            double distgreen =d;
	            distvalgreen[count]=(int) distgreen;
	            count++;
	        }
			
			/*
			 * get last value of red distances which
			 * treemap will have sorted as largest
			 * remember to multiply everything 
			 * by 10 to increase the plot to a 
			 * size easy to see.
			 */
			numkeyval--;
			int plotwidth = distvalred[numkeyval]; 
			plotwidth = (plotwidth*2)+10;
			/*
			 * get midpoint for largest value
			 * all other values will spread 
			 * from this point to form the Y.
			 */
			int midpoint =  plotwidth/2;
			
			//Plot the Red Distances onto Y Plot
			newRedIm = NewImage.createImage("V_Figure_Red", (plotwidth), plotheight, slicesval, bitDepth, NewImage.FILL_BLACK);
			newRedIm.show();
			PlacePixels(newRedIm,numkeyval,distvalred,midpoint);
			
			//Plot the Green Distances onto Y Plot
			newGreenIm = NewImage.createImage("V_Figure_Green", (plotwidth), plotheight, slicesval, bitDepth, NewImage.FILL_BLACK);
			newGreenIm.show();
			PlacePixels(newGreenIm,numkeyval,distvalgreen,midpoint);
			
		}
		
		public void PlacePixels(ImagePlus newIm, int numkeyval, int [] distval, int midpoint){
			int [] distvals = new int[numkeyval];
			distvals = distval;
			
			ImageProcessor ip = newIm.getProcessor();
			int remberlastY1 =3;
	
			/*
			 * Calculate colour gradient based on the 
			 * max distance measured.
			 */
			int thewidth = midpoint *2;
			int gradient = 255/thewidth;
			
			
			for(int pos=numkeyval;pos>-1;pos--){
				int y1 = 0;
				int x1 = 0;
				int x2 = 0;
				int y2 = 0;
				
				/*
				 * Set up for left hand pixels
				 */
				
				x1 = Math.abs(((distvals[pos])/2)-midpoint);
				y1 = remberlastY1+5;
				int colourVal = distvals[pos]*gradient;
				
				if(colourVal>255){
					colourVal = 255;
				}
				if(colourVal<25){
					colourVal = 25;
				}
					
				
				remberlastY1 = y1;
			
					ip.putPixel(x1, y1, colourVal);//Centrepixel of left spot
					//Fill in 5x5 spot
					
					//Middle Row
					for (int a=1;a<3;a++){
						ip.putPixel(x1+a, y1, colourVal);
					}
					for (int a=1;a<3;a++){
						ip.putPixel(x1-a, y1, colourVal);
					}
					ip.putPixel(x1, y1, colourVal);
					
					//Top Row
					for (int a=1;a<3;a++){
						ip.putPixel(x1-a, y1-2, colourVal);
					}
					for (int a=1;a<3;a++){
						ip.putPixel(x1+a, y1-2, colourVal);
					}
					ip.putPixel(x1, y1-2, colourVal);
					
					//Second Top Row
					for (int a=1;a<3;a++){
						ip.putPixel(x1-a, y1-1, colourVal);
					}
					for (int a=1;a<3;a++){
						ip.putPixel(x1+a, y1-1, colourVal);
					}
					ip.putPixel(x1, y1-1, colourVal);
					
					//Bottom Row
					for (int a=1;a<3;a++){
						ip.putPixel(x1-a, y1+2, colourVal);
					}
					for (int a=1;a<3;a++){
						ip.putPixel(x1+a, y1+2, colourVal);
					}
					ip.putPixel(x1, y1+2, colourVal);
					
					//Second Bottom Row
					for (int a=1;a<3;a++){
						ip.putPixel(x1-a, y1+1, colourVal);
					}
					for (int a=1;a<3;a++){
						ip.putPixel(x1+a, y1+1, colourVal);
					}
					ip.putPixel(x1, y1+1, colourVal);				
			
				/*
				 * Set up for right hand pixels	
				 */
					
				x2 = ((distvals[pos])/2)+midpoint;
				y2 = remberlastY1;
				
		
					ip.putPixel(x2, y2, colourVal);//Centrepixel of right spot
					//Fill in 5x5 spot
					
					//Middle Row
					for (int a=1;a<3;a++){
						ip.putPixel(x2+a, y2, colourVal);
					}
					for (int a=1;a<3;a++){
						ip.putPixel(x2-a, y2, colourVal);
					}
					ip.putPixel(x2, y2, colourVal);
					
					//Top Row
					for (int a=1;a<3;a++){
						ip.putPixel(x2-a, y2-2, colourVal);
					}
					for (int a=1;a<3;a++){
						ip.putPixel(x2+a, y2-2, colourVal);
					}
					ip.putPixel(x2, y2-2, colourVal);
					
					//Second Top Row
					for (int a=1;a<3;a++){
						ip.putPixel(x2-a, y2-1, colourVal);
					}
					for (int a=1;a<3;a++){
						ip.putPixel(x2+a, y2-1, colourVal);
					}
					ip.putPixel(x2, y2-1, colourVal);
					
					//Bottom Row
					for (int a=1;a<3;a++){
						ip.putPixel(x2-a, y2+2, colourVal);
					}
					for (int a=1;a<3;a++){
						ip.putPixel(x2+a, y2+2, colourVal);
					}
					ip.putPixel(x2, y2+2, colourVal);
					
					//Second Bottom Row
					for (int a=1;a<3;a++){
						ip.putPixel(x2-a, y2+1, colourVal);
					}
					for (int a=1;a<3;a++){
						ip.putPixel(x2+a, y2+1, colourVal);
					}
					ip.putPixel(x2, y2+1, colourVal);		
			}
			
		}
		
		public void MakeKymograph(double [] ScaledredDistance, TreeMap<Double, Object> SortedRedYPlotValues, TreeMap<Double, Object> SortedGreenYPlotValues,TreeMap<Double, Double> SortedRedMiddleValues, TreeMap<Double, Double> SortedGreenMiddleValues, int Counter){
			int plotwidth = 300;
			int plotheight = Counter;
			int bitDepth = 8;
			int slicesval =1;
			int colourVal = 0;
			
			/*
			 * Extract the ordered intensity values
			 * for the red channel
			 */
			int Ypos = 0;
			
			ImagePlus RedKymo = NewImage.createImage("Kymograph_Red", plotwidth, plotheight, slicesval, bitDepth, NewImage.FILL_BLACK);
		
			RedKymo.show();
			ImageProcessor ipRed = RedKymo.getProcessor();
			Set<Double>theRedKeys=SortedRedYPlotValues.keySet();		
			for(Double key: theRedKeys){
				double Keyred = key;
				Object redDistValues = SortedRedYPlotValues.get(Keyred);	//Get the plot values associated with the key
				Double middle = SortedRedMiddleValues.get(Keyred);	//Get the mid-point associated with the key
				
				//Extract the plot values associated with the key
				double [] redvals = new double[1000];
				if (redDistValues instanceof double[]){
					redvals=(double[])redDistValues;
				}
				
				int midpoint = middle.intValue();
				int theNums = redvals.length;
				
				//Left Side of Kymograph Image
				int placer = 0;
				for (int f=midpoint;f>0;f--){
					colourVal = (int) redvals[f];
					colourVal = colourVal/12;
					ipRed.putPixel(150-(placer), Ypos, colourVal);
					placer++;
				}
				placer =0;
				//Right Side of Kymograph Image
				for (int f=midpoint;f<(theNums);f++){
					colourVal = (int) redvals[f];
					colourVal = colourVal/12;
					ipRed.putPixel(150+placer, Ypos, colourVal);
					placer++;
				}
				Ypos++;
			}
			
			Ypos=0;
			ImagePlus GreenKymo = NewImage.createImage("Kymograph_Green", plotwidth, plotheight, slicesval, bitDepth, NewImage.FILL_BLACK);
			
			GreenKymo.show();
			ImageProcessor ipgreen = GreenKymo.getProcessor();
			Set<Double>theGreenKeys=SortedGreenYPlotValues.keySet();		
			for(Double key: theGreenKeys){
				double Keygreen = key;
				Object greenDistValues = SortedGreenYPlotValues.get(Keygreen);	//Get the plot values associated with the key
				Double middle = SortedGreenMiddleValues.get(Keygreen);	//Get the mid-point associated with the key
				
				//Extract the plot values associated with the key
				double [] greenvals = new double[1000];
				if (greenDistValues instanceof double[]){
					greenvals=(double[])greenDistValues;
				}
				
				int midpoint = middle.intValue();
				int theNums = greenvals.length;
				
				//Left Side of Kymograph Image
				int placer = 0;
				for (int f=midpoint;f>0;f--){
					colourVal = (int) greenvals[f];
					colourVal = colourVal/12;
					ipgreen.putPixel(150-(placer), Ypos, colourVal);
					placer++;
				}
				placer =0;
				//Right Side of Kymograph Image
				for (int f=midpoint;f<(theNums);f++){
					colourVal = (int) greenvals[f];
					colourVal = colourVal/12;
					ipgreen.putPixel(150+placer, Ypos, colourVal);
					placer++;
				}
				Ypos++;
			}
			
		}
		
		public void MakePlotData(TreeMap<Double, Object> SortedRedYPlotValues, TreeMap<Double, Object> SortedGreenYPlotValues,TreeMap<Double, Double> SortedRedMiddleValues, TreeMap<Double, Double> SortedGreenMiddleValues, int Counter){
			Set<Double>theGreenKeys=SortedGreenYPlotValues.keySet();	//All treemaps use the same keyset	
			Object redIntensityValues = null;
			Object greenIntensityValues = null;
			Double Redmiddle = null;
			Double Greenmiddle = null;
			
			TreeMap<Double,Double> GreenRatio = new TreeMap<Double,Double>();
			TreeMap<Double,Double> RedRatio = new TreeMap<Double,Double>();
			double avGreenPoint = 0;
			double avRedPoint = 0;

			for(Double key: theGreenKeys){
				double UnlockKey = key;
				greenIntensityValues = SortedGreenYPlotValues.get(UnlockKey);	//Get the green plot values associated with the key
				redIntensityValues = SortedRedYPlotValues.get(UnlockKey); //Get the red plot values associated with the key
				Redmiddle = SortedRedMiddleValues.get(UnlockKey);	//Get the mid-point associated with the key
				Greenmiddle = SortedGreenMiddleValues.get(UnlockKey);
			
				//Check 2 peaks were found in red channel
				if(Redmiddle>0){
			
					//Find Red Average Peak Intensity
					double [] redvals = new double[1000];
					if (redIntensityValues instanceof double[]){
						redvals=(double[])redIntensityValues;
					}
					//LeftPeak
					int numvals = redvals.length;
					Integer startpoint = Redmiddle.intValue();
						
					double LeftLarge = 0;
					double RightLarge = 0;
					for(int a=startpoint;a>0;a--){
						if (redvals[a]>LeftLarge){
							LeftLarge=redvals[a];
						}
					}
					//RightPeak
					for (int a=startpoint;a<numvals;a++){
						if (redvals[a]>RightLarge){
							RightLarge=redvals[a];
						}
					}
					if(Redmiddle==0){
						avRedPoint=0;
					}
					avRedPoint = (LeftLarge+RightLarge)/2;
			
				}
				
				if (Greenmiddle>0){
					//Find Green Average Peak Intensity
					double [] greenvals = new double[1000];
					if (greenIntensityValues instanceof double[]){
						greenvals=(double[])greenIntensityValues;
					}
					//LeftPeak
					int numvals = greenvals.length;
					Integer startpoint = Greenmiddle.intValue();
						
					double LeftLarge = 0;
					double RightLarge = 0;
					for(int a=startpoint;a>0;a--){
						if (greenvals[a]>LeftLarge){
							LeftLarge=greenvals[a];
						}
					}
					//RightPeak
					for (int a=startpoint;a<numvals;a++){
						if (greenvals[a]>RightLarge){
							RightLarge=greenvals[a];
						}
					}
					if(Greenmiddle==0){
						avGreenPoint=0;
					}	
					avGreenPoint = (LeftLarge+RightLarge)/2;
				}
				
				//Populate ratio treemaps
				
				GreenRatio.put(UnlockKey, avGreenPoint);
				
				RedRatio.put(UnlockKey, avRedPoint);
				
			}
			
			OutputRatioText(GreenRatio,RedRatio);
			
		}
		
		public void OutputRatioText(TreeMap<Double,Double>GreenRatio,TreeMap<Double,Double>RedRatio){
			String CreateName = "C:/Temp/RatioResults.txt";
			String FILE_NAME = CreateName;
			
			
			
			//Extract ordered Red distance and mean intensity from Treemap
	    	Set<Double>theKeys=RedRatio.keySet();
	
			
			try{
				FileWriter fileWriter = new FileWriter(FILE_NAME,true);
				BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
				
				bufferedWriter.newLine();
				bufferedWriter.write(" File= " + filename);
				bufferedWriter.newLine();
				
				for(Double key: theKeys){
					double Keyred = key;
					double redobj = RedRatio.get(Keyred);
					double greenobj = GreenRatio.get(Keyred);
					bufferedWriter.write("Distance = " + Keyred + " Red Mean Intensity = " + redobj + " Green Mean Intensity = " + greenobj);
					bufferedWriter.newLine();
		
				}
			
			
				bufferedWriter.close();

			}
			catch(IOException ex) {
				System.out.println("Error writing to file '" + FILE_NAME + "'");
        }
		}
		
		
		public void OutputText(int Counter, TreeMap<Double, Double> SortedRedIntensity, TreeMap<Double, Double> SortedGreenIntensity, TreeMap<Double, Double> SortedDistance){
			/*
			 * Method formats the intensity data into a text file 
			 * for import into Excel, R or Graphpad
			 */
			String CreateName = "C:/Temp/Results.txt";
			String FILE_NAME = CreateName;
	    	double [] greenMax = new double[Counter];
	    	double [] redMax = new double [Counter];
	    	double [] reddist = new double [Counter];
	    	double [] greendist = new double [Counter];
	    	
	    	//Extract ordered Red Maximum Intensity from Treemap
	    	Set<Double>theKeys=SortedRedIntensity.keySet();
			int count = 0;
			for(Double key: theKeys){
	            double Keyred = key;
	            Object redobj = SortedRedIntensity.get(Keyred);
	            Double d = null;
	            d = (Double) redobj;
	            double maxval =d;
	            redMax[count]=(int) maxval;
	            count++;
	        }
	    	
			//Extract ordered Green Maximum Intensity from Treemap
	    	Set<Double>theKeys2=SortedGreenIntensity.keySet();
			count = 0;
			for(Double key: theKeys2){
	            double Keygreen = key;
	            Object greenobj = SortedGreenIntensity.get(Keygreen);
	            Double d = null;
	            d = (Double) greenobj;
	            double maxval =d;
	            greenMax[count]=(int) maxval;
	            count++;
	        }
			
			//Extract ordered Green & Red Distances from Treemap
	    	Set<Double>theKeys3=SortedDistance.keySet();
			
			count = 0;
			for(Double key: theKeys3){
				reddist[count] = key;
	            double dist = key;
	            Object distgreenobj = SortedDistance.get(dist);
	            Double d = null;
	            d = (Double) distgreenobj;
	            double distval =d;
	            greendist[count]=(int) distval;
	            count++;
	        }
	    	
	    	
			try{
				FileWriter fileWriter = new FileWriter(FILE_NAME,true);
				BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
				
				
					bufferedWriter.newLine();
					bufferedWriter.newLine();
					bufferedWriter.write(" File= " + filename);
					bufferedWriter.newLine();
					for (int t=0;t<Counter;t++){
						bufferedWriter.write("Green Distance = " + greendist[t] + " Green Max Spot Intensity = " + greenMax[t]);
						bufferedWriter.newLine();
						bufferedWriter.write(" Red distance = " + reddist[t] + " Red Max Spot Intensity = " + redMax[t]);
						bufferedWriter.newLine();
						bufferedWriter.newLine();
					}
				
				
				
				bufferedWriter.close();

			}
			catch(IOException ex) {
	            System.out.println(
	                "Error writing to file '"
	                + FILE_NAME + "'");
	        }
		}
		
		public void OutputProfiles(int Counter,TreeMap<Double, Object> SortedRedYPlotValues, TreeMap<Double, Object> SortedGreenYPlotValues){
			
			String CreateName = "C:/Temp/ProfileResults.txt";
			String FILE_NAME = CreateName;
			int first = 1;
			Set<Double>theKeys=SortedGreenYPlotValues.keySet();		
			for(Double key: theKeys){
				double Keygreen = key;
				Object greenDistValues = SortedGreenYPlotValues.get(Keygreen);	//Get the green plot values associated with the key
				Object redDistValues = SortedRedYPlotValues.get(Keygreen);	//Get the green plot values associated with the key
				
				//Extract the plot values associated with the distance key
				double [] greenvals = new double[1000];
				if (greenDistValues instanceof double[]){
					greenvals=(double[])greenDistValues;
				}
				double [] redvals = new double[1000];
				if (redDistValues instanceof double[]){
					redvals=(double[])redDistValues;
				}
				
				int numval = redvals.length;
			
				try{
					FileWriter fileWriter = new FileWriter(FILE_NAME,true);
					BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
				
						bufferedWriter.newLine();
						if(first==1){
							bufferedWriter.write(" File= " + filename);
							bufferedWriter.newLine();
							first=0;
						}		
						bufferedWriter.newLine();
						bufferedWriter.write("Distance = " + Keygreen + " GreenProfile = ");
						for(int g=0;g<numval;g++){
							bufferedWriter.write((int) greenvals[g]+",");
						}
						bufferedWriter.newLine();
						bufferedWriter.write("Distance = " + Keygreen + " RedProfile = ");
						for(int g=0;g<numval;g++){
							bufferedWriter.write((int) redvals[g]+",");
						}
						bufferedWriter.newLine();
						bufferedWriter.close();

				}
				catch(IOException ex) {
					System.out.println(
							"Error writing to file '"
									+ FILE_NAME + "'");
				}
			}
		}
		
}
