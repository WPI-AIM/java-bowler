package com.neuronrobotics.sdk.addons.kinematics;

import java.util.ArrayList;

import javax.swing.JFrame;

import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.util.ThreadUtil;

import Jama.Matrix;
public  class DHChain {
	private ArrayList<DHLink> links = new ArrayList<DHLink>();
	private ArrayList<TransformNR> chain = new ArrayList<TransformNR>();
	private ArrayList<TransformNR> intChain = new ArrayList<TransformNR>();
	private final double[] upperLimits;
	private final double[] lowerLimits;
	private boolean debug=false;

	DhInverseSolver is;
	public DHChain(double [] upperLimits,double [] lowerLimits, boolean debugViewer ) {
		this(upperLimits, lowerLimits);

	}
	
	public DHChain(double [] upperLimits,double [] lowerLimits ) {
		
		this.upperLimits = upperLimits;
		this.lowerLimits = lowerLimits;
		getLinks().add(new DHLink(	13, 	Math.toRadians(180), 	32, 	Math.toRadians(-90)));//0->1
		getLinks().add(new DHLink(	25, 	Math.toRadians(-90), 	93, 	Math.toRadians(180)));//1->2
		getLinks().add(new DHLink(	11, 	Math.toRadians(90), 	24, 	Math.toRadians(90)));//2->3 
		getLinks().add(new DHLink(	128, 	Math.toRadians(-90), 		0, 		Math.toRadians(90)));//3->4
		
		getLinks().add(new DHLink(	0, 		Math.toRadians(0), 			0, 		Math.toRadians(-90)));//4->5
		getLinks().add(new DHLink(	25, 	Math.toRadians(90), 		0, 		Math.toRadians(0)));//5->tool
		
		forwardKinematics(new  double [] {0,0,0,0,0,0});
	}

	public double[] inverseKinematics(TransformNR target,double[] jointSpaceVector )throws Exception {
		
		if(getLinks() == null)
			return null;
		long start = System.currentTimeMillis();
		
		
		//is = new GradiantDecent(this,debug);
		//is = new SearchTreeSolver(this,debug);
		if(is == null)
			is = new ComputedGeometricModel(this,debug);
		
		double [] inv = is.inverseKinematics(target, jointSpaceVector);	
		if(debug){
			//getViewer().updatePoseDisplay(getChain(jointSpaceVector));
		}
		
		//System.out.println("Inverse Kinematics took "+(System.currentTimeMillis()-start)+"ms");
		return inv;
	}

	public TransformNR forwardKinematics(double[] jointSpaceVector) {
		return forwardKinematics(jointSpaceVector, false);
	}
	
	public TransformNR forwardKinematics(double[] jointSpaceVector, boolean store) {
		return new TransformNR(forwardKinematicsMatrix(jointSpaceVector, store) );
	}
	/**
	 * Gets the task space velocites from the joint space velocity vector
	 * @param jointSpaceVelocityVector the joint velocities
	 * @return a matrix of the task space velocities
	 */
	public Matrix getTaskSpaceVelocites(double[] jointSpaceVelocityVector,double[] jointSpaceVector, TransformNR currentTip){
		double [][] data = new double[6][getLinks().size()]; 
		getChain(jointSpaceVector);
		for(int i=0;i<getLinks().size();i++){
			
			double [] zVect = new double [3];
			
			if(i==0){
				zVect[0]=0;
				zVect[1]=0;
				zVect[2]=1;
			}else{
				//Get the rz vector from matrix
				zVect[0]=chain.get(i-1).getRotationMatrix().getRotationMatrix()[0][2];
				zVect[1]=chain.get(i-1).getRotationMatrix().getRotationMatrix()[1][2];
				zVect[2]=chain.get(i-1).getRotationMatrix().getRotationMatrix()[2][2];
			}
			//Assume all rotational joints
			//Set to zero if prismatic
			data[i][3]=zVect[0];
			data[i][4]=zVect[1];
			data[i][5]=zVect[2];
			
			//Figure out the current 
			Matrix current = new TransformNR().getMatrixTransform();
			for(int j=i;j<getLinks().size();j++) {
				Matrix step = getLinks().get(j).DhStepRotory(Math.toRadians(jointSpaceVector[j]));
				//System.out.println("Current:\n"+current+"Step:\n"+step);
				current = current.times(step);
			}
			double [] rVect = new double [3];
			TransformNR tmp = new TransformNR(current);
			rVect[0]=tmp.getX();
			rVect[1]=tmp.getY();
			rVect[2]=tmp.getZ();
			
			double [] xProd = new double [3];
			
			//Cross product of rVect and Z vect
			
			data[i][0]=xProd[0];
			data[i][1]=xProd[1];
			data[i][2]=xProd[2];
			
		}
		
		return new Matrix(data);
	}
	
	public Matrix forwardKinematicsMatrix(double[] jointSpaceVector, boolean store) {
		if(getLinks() == null)
			return new TransformNR().getMatrixTransform();
		if (jointSpaceVector.length!=getLinks().size())
			throw new IndexOutOfBoundsException("DH links do not match defined links");
		Matrix current = new TransformNR().getMatrixTransform();
		if(store)
			setChain(new ArrayList<TransformNR>());
		for(int i=0;i<getLinks().size();i++) {
			Matrix step = getLinks().get(i).DhStepRotory(Math.toRadians(jointSpaceVector[i]));
			//System.out.println("Current:\n"+current+"Step:\n"+step);
			current = current.times(step);
			if(store){
				intChain.add(new TransformNR(step));
				chain.add(new TransformNR(current));
			}
		}
		//System.out.println("Final:\n"+current);
		return current;
	}

	public void setChain(ArrayList<TransformNR> chain) {
		this.chain = chain;
	}

	public ArrayList<TransformNR> getChain(double[] jointSpaceVector) {
		forwardKinematics(jointSpaceVector,true);
		return chain;
	}
	

	public static void main(String [] args){

		DHChain tk = new DHChain(new double[]{90,90,90,90,90,90}, new double[]{-90,-90,-90,-90,-90,-90}, true);

		ThreadUtil.wait(2000);
//		Transform target = new Transform(new Matrix(new double [][] {
//				{ -000.46,	0000.63,	-000.63,	-252.29	 },
//				{ 0000.21,	0000.76,	0000.61,	0051.07	 },
//				{ 0000.86,	0000.15,	-000.48,	0083.43	 },
//				{ 0000.00,	0000.00,	0000.00,	0001.00	 }
//				}));
		// the expected Joint space vector is { -10,45,-45,45,45,45}
		//double [] targetVect = new double [] { -85,10,-90,0,90,90};
		double [] targetVect = new double [] { -10,45,-45,45,45,45};
		TransformNR target = tk.forwardKinematics(targetVect);
		TransformNR home = tk.forwardKinematics(new  double [] {0,0,0,0,0,0});
		try {
			double [] back = tk.inverseKinematics(target, new  double [] {0,0,0,0,0,0});
			System.out.print("\nJoint angles targeted: {");
			for(int i=0;i<6;i++){
				System.out.print(" "+targetVect[i]);
			}
			System.out.print("} \n");
			System.out.print("\nJoint angles difference: {");
			for(int i=0;i<6;i++){
				System.out.print(" "+(back[i]-targetVect[i]));
			}
			System.out.print("} \n");
			//System.out.println("Attempted\n"+target+"\nArrived at \n"+tk.forwardKinematics(back));
			ThreadUtil.wait(5000);
			
			back = tk.inverseKinematics( home,back);
			System.out.print("\nJoint angles targeted: {");
			for(int i=0;i<6;i++){
				System.out.print(" "+0);
			}
			System.out.print("} \n");
			System.out.print("\nJoint angles difference: {");
			for(int i=0;i<6;i++){
				System.out.print(" "+(back[i]));
			}
			System.out.print("} \n");
			//System.out.println("Attempted\n"+target+"\nArrived at \n"+tk.forwardKinematics(back));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

//	public void setViewer(DHViewer viewer) {
//		this.viewer = viewer;
//	}
//
//	public DHViewer getViewer() {
//		return viewer;
//	}

	public double[] getUpperLimits() {
		// TODO Auto-generated method stub
		return upperLimits;
	}

	public double[] getlowerLimits() {
		// TODO Auto-generated method stub
		return lowerLimits;
	}

	public void setLinks(ArrayList<DHLink> links) {
		this.links = links;
	}

	public ArrayList<DHLink> getLinks() {
		return links;
	}

}
