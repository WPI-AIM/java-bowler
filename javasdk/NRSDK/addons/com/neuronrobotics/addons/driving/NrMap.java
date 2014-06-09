package com.neuronrobotics.addons.driving;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.neuronrobotics.addons.driving.virtual.ObsticleType;

public class NrMap extends JPanel{

	/**
	 * 
	 */
	private static final long serialVersionUID = -1487461776000494761L;
	
	private double pixelToCm=100;
	JLabel lab=new JLabel();
	private BufferedImage display;
	
	private ArrayList<userDefinedObsticles> obs = new ArrayList<userDefinedObsticles>();
	
	protected static final double width = 1024;
	protected static final double height = 1024;
	
	/**
	 * Instantiate a robot map using a default blank map. 
	 */
	public NrMap(){
		initGui();
	}
	/**
	 * Instantiate a robot map using a provided map
	 * @param b the image of the desired map
	 */
	public NrMap(BufferedImage b){
		setDisplay(b);
		initGui();
	}
	
	protected void initGui(){ 
        removeAll();
        updateMap();
        add(lab); 
	}
	
	protected void updateMap() {
		//System.out.println("Updating Map");
		BufferedImage display = getMap();
		setFinalDisplayImage(display);
	}
	
	protected void setFinalDisplayImage(BufferedImage d){
		lab.setIcon(new ImageIcon(d ) );
		lab.setVisible(true);
	}
	
	protected void setDisplay(BufferedImage d){
		display=d;
	}
	
	public BufferedImage getMap() {
		if(display==null){
			return new BufferedImage((int)width,(int) height,BufferedImage.TYPE_INT_RGB);
		}
		BufferedImage d = new BufferedImage(display.getWidth(), display.getHeight(),BufferedImage.TYPE_INT_RGB);
		Graphics2D g =d.createGraphics();
		g.drawImage(display,0, 0, null);
		
		for(userDefinedObsticles o:obs) {
			o.drawUserObsticles(g);
		}
		return d;
	}
	public void removeAllUserDefinedObsticles(){
		obs.clear();
	}
	public void addUserDefinedObsticle(int x, int y, int size,ObsticleType type){
		if(display==null){
			display =  new BufferedImage((int)width,(int) height,BufferedImage.TYPE_INT_RGB);
		}
		obs.add(new userDefinedObsticles(x,y,size,type));
		
	}
	
	public ObsticleType getObsticle(int x,int y) {
		return ObsticleType.get(new Color(getMap().getRGB(x, y)));
	}
	
	public double getPixelToCm(int pix){
		return ((double)pix)/(pixelToCm);
	}
	public double getCmToPixel(double cm){
		return (cm*(pixelToCm));
	}
	
	private class userDefinedObsticles{
		ObsticleType type;
		public userDefinedObsticles(int x2, int y2, int size2,ObsticleType type) {
			this.type = type;
			setX(x2);
			setY(y2);
			setSize(size2);
		}

		public void drawUserObsticles(Graphics2D g) {
			g.setColor(type.getValue());
			g.fillRect(getX()-(getSize()/2),getY()-(getSize()/2), getSize(),getSize());
		}

		public void setX(int x) {
			this.x = x;
		}

		public int getX() {
			return x;
		}

		public void setY(int y) {
			this.y = y;
		}

		public int getY() {
			return y;
		}

		public void setSize(int size) {
			this.size = size;
		}

		public int getSize() {
			return size;
		}

		private int x;
		private int y;
		private int size;
	}
	public void setUserDefinedData(ArrayList<DataPoint> data,ObsticleType type) {
		 //removeAllUserDefinedObsticles();
		 for(DataPoint d:data){
			 double pix =  getCmToPixel(d.getRange()/100);
			 double centerX=(width/2);
			 double centerY=(height/2);
			 if(!(pix>centerX || pix>centerY )){
				 double deltX = pix*Math.cos(Math.toRadians(d.getAngle()));
				 double deltY = pix*Math.sin(Math.toRadians(d.getAngle()));
				 addUserDefinedObsticle((int)(centerX+deltX), (int)(centerY+deltY), 2,type);
			 }else{
				 //System.out.println("Range too long: "+pix+" cm="+d.getRange()/100);
			 }
		 }
		 updateMap();
	}
	
}
