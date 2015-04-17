package com.neuronrobotics.sdk.addons.kinematics;

import java.io.InputStream;
import java.util.ArrayList;

import javafx.scene.transform.Affine;
import Jama.Matrix;

import com.neuronrobotics.sdk.addons.kinematics.gui.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.addons.kinematics.xml.XmlFactory;
import com.neuronrobotics.sdk.dyio.DyIO;
import com.neuronrobotics.sdk.pid.GenericPIDDevice;


public class DHParameterKinematics extends AbstractKinematicsNR {
	
	private DHChain chain=null;

	private ArrayList<Affine> linksListeners = new ArrayList<Affine>();
	public DHParameterKinematics() {
		this((DyIO)null,"TrobotLinks.xml");
	}
	
	public DHParameterKinematics( DyIO dev) {
		this(dev,"TrobotLinks.xml");

	}
	public DHParameterKinematics( DyIO dev, String file) {
		this(dev,XmlFactory.getDefaultConfigurationStream(file),XmlFactory.getDefaultConfigurationStream(file));
		
	}
	public DHParameterKinematics( GenericPIDDevice dev) {
		this(dev,"TrobotLinks.xml");

	}
	public DHParameterKinematics( GenericPIDDevice dev, String file) {
		this(dev,XmlFactory.getDefaultConfigurationStream(file),XmlFactory.getDefaultConfigurationStream(file));
		
	}
	public DHParameterKinematics( DyIO dev, InputStream linkStream, InputStream dhStream) {
		super(linkStream,new LinkFactory( dev));
		setChain(new DHChain(dhStream,getFactory()));
	}
	
	public DHParameterKinematics(GenericPIDDevice dev, InputStream linkStream, InputStream dhStream) {
		super(linkStream,new LinkFactory( dev));
		setChain(new DHChain(dhStream,getFactory()));
	}

	public DHParameterKinematics(InputStream linkStream, InputStream dhStream) {
		super(linkStream,new LinkFactory());
		setChain(new DHChain(dhStream,getFactory()));
	}

	@Override
	public double[] inverseKinematics(TransformNR taskSpaceTransform)throws Exception {
		return getDhChain().inverseKinematics(taskSpaceTransform, getCurrentJointSpaceVector());
	}

	@Override
	public TransformNR forwardKinematics(double[] jointSpaceVector) {
		if(jointSpaceVector == null || getDhChain() == null)
			return new TransformNR();
		TransformNR rt = getDhChain().forwardKinematics(jointSpaceVector);
		ArrayList<TransformNR> joints = getChainTransformations();
		for(int i=0;i<joints.size();i++)		{
			TransformFactory.getTransform(joints.get(i), linksListeners.get(i));
		}
		return rt;
	}
	
	/**
	 * Gets the Jacobian matrix
	 * @return a matrix representing the Jacobian for the current configuration
	 */
	public Matrix getJacobian(){
		long time = System.currentTimeMillis();
		Matrix m = getDhChain().getJacobian(getCurrentJointSpaceVector());
		System.out.println("Jacobian calc took: "+(System.currentTimeMillis()-time));
		return m;
	}
	
	public ArrayList<TransformNR> getChainTransformations(){
		return getChain().getChain(getCurrentJointSpaceVector());
	}

	public void setDhChain(DHChain chain) {
		this.setChain(chain);
	}

	public DHChain getDhChain() {
		return getChain();
	}

	public DHChain getChain() {
		return chain;
	}

	public void setChain(DHChain chain) {
		this.chain = chain;
		for(DHLink dh:chain.getLinks()){
			Affine a = new Affine();
			dh.setListener(a);
			linksListeners.add(a);
		}
	}


}
