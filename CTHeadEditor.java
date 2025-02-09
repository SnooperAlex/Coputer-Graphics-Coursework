
/*
CS-255 Getting started code for the assignment
I do not give you permission to post this code online
Do not post your solution online
Do not copy code
Do not use JavaFX functions or other libraries to do the main parts of the assignment:
	1. Creating a resized image (you must implement nearest neighbour and bilinear interpolation yourself
	2. Gamma correcting the image
	3. Creating the image which has all the thumbnails and event handling to change the larger image
All of those functions must be written by yourself
You may use libraries / IDE to achieve a better GUI
*/
import java.io.*;
import java.util.TooManyListenersException;

import javafx.application.Application;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Toggle;
//import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;  
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class CTHeadEditor extends Application {
	short cthead[][][]; //store the 3D volume data set
	float grey[][][]; //store the 3D volume data set converted to 0-1 ready to copy to the image
	float[] lookup = new float[256];
	short min, max; //min/max value in the 3D volume data set
	ImageView TopView;
	int currentNum = 76;
	double currentSize = 256;
	double currentGamma = 1;
	int origHeight = 256;
	int origWidth = 256;


    @Override
    public void start(Stage stage) throws FileNotFoundException {
		//create the first lookup table
		for (int x = 0; x < 256; x++){
			double result =  Math.pow(x/255.0, 1.0/currentGamma);
			lookup[x] = (float) result;
		}

		stage.setTitle("CThead Viewer");
		
		try {
			ReadData();
		} catch (IOException e) {
			System.out.println("Error: The CThead file is not in the working directory");
			System.out.println("Working Directory = " + System.getProperty("user.dir"));
			return;
		}
		
		//int width=1024, height=1024; //maximum size of the image
		//We need 3 things to see an image
		//1. We need to create the image
		Image top_image=GetSlice(); //go get the slice image
		//2. We create a view of that image
		TopView = new ImageView(top_image); //and then see 3. below

		//Create the simple GUI
		final ToggleGroup group = new ToggleGroup();

		RadioButton rb1 = new RadioButton("Nearest neighbour");
		rb1.setToggleGroup(group);
		rb1.setSelected(true);

		RadioButton rb2 = new RadioButton("Bilinear");
		rb2.setToggleGroup(group);

		Slider szslider = new Slider(32, 1024, 256);
		
		Slider gamma_slider = new Slider(.1, 4, 1);

		//Radio button changes between nearest neighbour and bilinear
		group.selectedToggleProperty().addListener(new ChangeListener<Toggle>() {
			public void changed(ObservableValue<? extends Toggle> ob, Toggle o, Toggle n) {
 
				if (rb1.isSelected()) {
					System.out.println("Radio button 1 clicked");
				} else if (rb2.isSelected()) {
					System.out.println("Radio button 2 clicked");
				}
            }
        });
		
		//Size of main image changes (slider)
		szslider.valueProperty().addListener(new ChangeListener<Number>() { 
			public void changed(ObservableValue <? extends Number >  
					observable, Number oldValue, Number newValue) { 

				System.out.println(newValue.intValue());
				//Here's the basic code you need to update an image
				 //clear the old image
				TopView.setImage(null); //go get the slice image
				Image thenewImage;
				currentSize = szslider.getValue();
				if (rb1.isSelected()){
					thenewImage = resize(currentSize);
				}
				else{
					thenewImage = bilinear(currentSize);
				}
				TopView.setImage(thenewImage); //Update the GUI so the new image is displayed
				
            } 
        });
		
		//Gamma value changes
		gamma_slider.valueProperty().addListener(new ChangeListener<Number>() { 
			public void changed(ObservableValue <? extends Number >  
						observable, Number oldValue, Number newValue) {  

				System.out.println(newValue.doubleValue());
				setGamma(gamma_slider.getValue());
				gammaTable(getGamma());
				Image thenewImage;
				if (rb1.isSelected()){
					thenewImage = resize(currentSize);
				}
				else{
					thenewImage = bilinear(currentSize);
				}
				TopView.setImage(thenewImage);
			}
		});
		
		VBox root = new VBox();

		//Add all the GUI elements
        //3. (referring to the 3 things we need to display an image)
      	//we need to add it to the layout
		root.getChildren().addAll(rb1, rb2, gamma_slider,szslider, TopView);

		//Display to user
        Scene scene = new Scene(root, 1024, 768);
        stage.setScene(scene);
        stage.show();
        
        ThumbWindow(scene.getX()+200, scene.getY()+200);
    }
    

	//Function to read in the cthead data set
	public void ReadData() throws IOException {
		//File name is hardcoded here - much nicer to have a dialog to select it and capture the size from the user
		File file = new File("CThead");
		//Read the data quickly via a buffer (in C++ you can just do a single fread - I couldn't find the equivalent in Java)
		DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
		
		int i, j, k; //loop through the 3D data set
		
		min=Short.MAX_VALUE; max=Short.MIN_VALUE; //set to extreme values
		short read; //value read in
		int b1, b2; //data is wrong Endian (check wikipedia) for Java so we need to swap the bytes around
		
		cthead = new short[113][256][256]; //allocate the memory - note this is fixed for this data set
		grey= new float[113][256][256];
		//loop through the data reading it in
		for (k=0; k<113; k++) {
			for (j=0; j<256; j++) {
				for (i=0; i<256; i++) {
					//because the Endianess is wrong, it needs to be read byte at a time and swapped
					b1=((int)in.readByte()) & 0xff; //the 0xff is because Java does not have unsigned types (C++ is so much easier!)
					b2=((int)in.readByte()) & 0xff; //the 0xff is because Java does not have unsigned types (C++ is so much easier!)
					read=(short)((b2<<8) | b1); //and swizzle the bytes around
					if (read<min) min=read; //update the minimum
					if (read>max) max=read; //update the maximum
					cthead[k][j][i]=read; //put the short into memory (in C++ you can replace all this code with one fread)
				}
			}
		}
		System.out.println(min+" "+max); //diagnostic - for CThead this should be -1117, 2248
		//(i.e. there are 3366 levels of grey, and now we will normalise them to 0-1 for display purposes
		//I know the min and max already, so I could have put the normalisation in the above loop, but I put it separate here
		for (k=0; k<113; k++) {
			for (j=0; j<256; j++) {
				for (i=0; i<256; i++) {
					grey[k][j][i]=((float) cthead[k][j][i]-(float) min)/((float) max-(float) min);
				}
			}
		}
		
	}
	
	//Gets an image from slice 76
	public Image GetSlice() {
		WritableImage image = new WritableImage(256, 256);
		//Find the width and height of the image to be process
		int width = (int)image.getWidth();
		int height = (int)image.getHeight();
		float val;
		//Get an interface to write to that image memory
		PixelWriter image_writer = image.getPixelWriter();
		//Iterate over all pixels
		for(int y = 0; y < height; y++) {
			for(int x = 0; x < width; x++) {
		//For each pixel, get the colour from the cthead slice 76
				val=grey[76][y][x];
				Color color=Color.color(val,val,val);
		//Apply the new colour
				image_writer.setColor(x, y, color);
				}
			}
		return image;
		}
	
	public Image resize(double value){
		WritableImage newImage = new WritableImage((int) value, (int) value);
		//Find the width and height of the image to be process

		float newWidth = (int) value;
		float newHeight = (int) value;
		float val;

		//Get an interface to write to that image memory
		PixelWriter image_writer = newImage.getPixelWriter();

		//Iterate over all pixels
		for(int j = 0; j < newWidth; j++) {
			for(int i = 0; i < newHeight; i++) {


				int x = (int) (j * origWidth/newWidth);
				int y = (int) (i * origHeight/newHeight);

				val = grey[getNum()][y][x];

				val = gammaChange(val, origWidth);

				Color color=Color.color(val, val, val);
				
				//Apply the new colour
				image_writer.setColor(j, i, color);
			}
		}
		return newImage;
		}

	public Image bilinear(double value){
		WritableImage newImage = new WritableImage((int) value, (int) value);

		//Find the width and height of the image to be process
		float newWidth = (int) value;
		float newHeight = (int) value;
		float val;
		float val2;
		float val3;
		float val4;

		//Get an interface to write to that image memory
		PixelWriter image_writer = newImage.getPixelWriter();

		for(int j = 0; j < newHeight; j++) {
			for(int i = 0; i < newWidth; i++) {

				float x = (j * origWidth/newWidth);
				float y = (i * origHeight/newHeight);
				
				if ((x <= 254) && (y <=254)){
					
					//the coordinates around the pixel
					int ax = (int) Math.floor(x);
					int ay = (int) Math.floor(y);
					int bx = (int) Math.floor(x)+1;
					int by = (int) Math.floor(y);
					int cx = (int) Math.floor(x);
					int cy = (int) Math.floor(y)+1;
					int dx = (int) Math.floor(x)+1 ;
					int dy = (int) Math.floor(y)+1;

					//the values for every pixel
					val= grey[getNum()][ax][ay];
					val2 = grey[getNum()][bx][by];
					val3= grey[getNum()][cx][cy];
					val4 = grey[getNum()][dx][dy];

					//bilinear interpolation
					float v1 = val + (val2 - val)*((x - ax)/(bx-ax));
					float v2 = val3 + (val4 - val3)*((x - ax)/(bx-ax));
					float v = v1+ (v2 - v1)*((y - ay)/(cy-ay));

					if( v < 0){
						v = 0;
					}
					if (v > 1){
						v=1;
					}

					v = gammaChange(v, origWidth);

					Color color=Color.color(v, v, v);
					
					//Apply the new colour
					image_writer.setColor(i, j, color);

				}
			}															
		}
		return newImage;
	}		
			
	//search fpr the needed gamma
	public float gammaChange(double value, double size){
			
			float col = lookup[(int) Math.floor(value * 255)];	
			return col;										
			}

	//create a lookup table
	public void gammaTable(double gamma){
		for (int x = 0; x < 256; x++){
			double result =  Math.pow(x/255.0, 1.0/gamma);
			lookup[x] = (float) result;
		}
	}
	
	public void ThumbWindow(double atX, double atY) {
		StackPane ThumbLayout = new StackPane();
		
		//creates a blank image of size 800
		WritableImage thumb_image = new WritableImage(800, 800);
		ImageView thumb_view = new ImageView(thumb_image);
		ThumbLayout.getChildren().add(thumb_view);

		float val;
				
		PixelWriter image_writer = thumb_image.getPixelWriter();

		for(int y = 0; y < thumb_image.getHeight(); y++) {
			for(int x = 0; x < thumb_image.getWidth(); x++) {
						Color color=Color.color(1,1,1);						
						image_writer.setColor(x, y, color);					
			}
		}

		//variables which will create every small image, evey image will be of size 60, and have a gap of 5 pixels, total is 65 pixels between image and gap
		int nextX = 0;
		int nextY = 0;

		//draw every imaage for every slice
		for(int slice = 0; slice < 113; slice++){
			//if the row is filled, then move to the next row
			if (nextX >= 750){
				nextX = 0;
				nextY += 65;
			}
			for(int j = 0; j < 60; j++) {
				for(int i = 0; i < 60; i++) {
					//create images of size 60 and draw them on the thumbnail

					int x = (int) (j * 256/60);
					int y = (int) (i * 256/60);
					val=grey[slice][y][x];
					Color color=Color.color(val,val,val);

					image_writer.setColor(j+nextX, i+nextY, color);
					
					
				}
			}			
			//next image	
			nextX += 65;
		}
		
		
		Scene ThumbScene = new Scene(ThumbLayout, thumb_image.getWidth(), thumb_image.getHeight());
		
		//Add mouse over handler - the large image is change to the image the mouse is over
		thumb_view.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_MOVED, event -> {
			System.out.println(event.getX()+"  "+event.getY());
			event.consume();
			int m = (int) ((event.getX() / 65 ));
			int n = (int) ((event.getY() / 65 ));
			int z = (int) (m + (n * 11 + n));
			setNum(z);
			TopView.setImage(resize(currentSize));
			
		});
	
		//Build and display the new window
		Stage newWindow = new Stage();
		newWindow.setTitle("CThead Slices");
		newWindow.setScene(ThumbScene);
	
		// Set position of second window, related to primary window.
		newWindow.setX(atX);
		newWindow.setY(atY);
	
		newWindow.show();
	}


		public void setNum(int currentNum){
			this.currentNum = currentNum;
		}

		public int getNum(){
			return currentNum;
		}

		public void setGamma(double d){
			this.currentGamma = d;
		}

		public double getGamma(){
			return currentGamma;
		}
		
    public static void main(String[] args) {
        launch();
    }

}