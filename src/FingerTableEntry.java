public class FingerTableEntry {
	public int start;//The start value for each finger table entry
	public NodeInfo successor;//The corresponding successor Chord Node info for the start value.
	
	/**
	 * Dummy constructor
	 * @param null
	 * @return null
	 */
	public FingerTableEntry(){
		this.start = 0;
		this.successor = null;
	}

	/**
	 * Parametrized constructor
	 * @param start The start value for each finger table entry
	 * @param n The successor Chord Node instance information object
	 * @return null
	 */
	public FingerTableEntry(int start, NodeInfo n){
		this.start = start;
		this.successor = n;
	}
	
	//Setters and Getters for the data members
	public int getStart() {
		return start;
	}
	public void setStart(int start) {
		this.start = start;
	}
	public NodeInfo getSuccessor() {
		return successor;
	}
	public void setSuccessor(NodeInfo successor) {
		this.successor = successor;
	}	
}
