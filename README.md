# Y_Profiler
ImageJ plugin to take line profiles and order them by distance between 2 peaks

INSTALLATION

    Ensure that the ImageJ version is at least 1.5 and the installation has Java 1.8.0_60 (64bit) or higher installed. If not download the latest version of ImageJ bundled with Java and install it.

    The versions can be checked by opening ImageJ and clicking Help then About ImageJ.

    Download the latest copy of Bio-Formats into the ImageJ plugin directory.

    Create a directory in the C: drive called Temp (case sensitive)

    Using notepad save a blank .txt files called ProfileResults.txt, Results.txt and RatioResults.txt into the Temp directory you previously created (also case sensitive).

    Place Y_Profile_Maker.jar into the plugins directory of your ImageJ installation, a plugin called Y Profile Maker should appear in the Plugins drop down menu on ImageJ.
    
    There is a companion .jar file called TextMeasure that will quickly collate all the distance and intensity measurements into 1 file. Its a standalone Java program that doesn't require ImageJ, its written to work on the ProfileResults.txt file output.

    YProfileTextPeaks.java and Y_Profile_Maker.java are the files containing the editable code for the standalone java program and the ImageJ plugin should improvements or changes be required.

USAGE

    You will be prompted to Open an Image. The plugin was written for 3 channel, Z stack, timelapse images acquired Green channel then Red Channel, the third channel can be anything as its discarded. It will probably work on non timelapse, non Z stack images but it will cause problems if the channel order is reversed.

    When the Bio-Formats dialogue opens make sure that the only tick is in Split Channels, nothing else should be ticked.

    Once the images have opened the red channel will be selected with the line tool activated. You will be prompted to Draw a line across a cell, it doesn't matter if the cell has only 1 dot on the profile as this will make 1 peak and will form part of the stalk on the Y profile plot. Click OK once you are happy with the line and the plugin will find the peaks on the line and apply the same line to the green channel and see if there are any peaks there. The plugin will ask if you want to do another, if you do another you can move throughout the timelapse to find cells of interest. NOTE be careful not to change the active image to the green channel when you draw the line or you will measure the wrong channel.

    Results are saved to the 3 text files you should have created in C:\Temp

    The ProfileResults.txt file contains all the calculated distance between peaks values and the raw values from the line profiles from each channel in case there are any issues. This allows the data to be re-analysed in another program such as Matlab where there are far more sensitive peak finding programs.
    
    The Results.txt file contains the distance between peaks and the maximum spot intensity in both channels.
    
    The RatioResults.txt file contains the distance between peaks and the mean intensity of both spots (or 1 if only 1 was found) in both channels.

