package myBIT;

public class ThreadInstrumentationOutput {
    public int threadID;
    public int blockCount;
    
    public int requestXmin;
	public int requestYmin;
	
	public int requestXmax;
	public int requestYmax;
	
	public String requestScan;
    
    public ThreadInstrumentationOutput (int tID) {
        this.threadID = tID;
        this.blockCount = 0;
    }

    public int getArea() {
        return (this.requestXmax-this.requestXmin) * (this.requestYmax-this.requestYmin);
    }
    
    public String toString() {
        return "Thread "+this.threadID
                +":\nBlocks: "+this.blockCount
                +"\nScan: "+this.requestScan
                +"\nSquare: ("+this.requestXmin+","+this.requestYmin+") to ("
                                +this.requestXmax+","+this.requestYmax+")"
                +"\nArea: "+this.getArea();
    }
}