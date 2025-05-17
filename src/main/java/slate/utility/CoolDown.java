package slate.utility;

public class CoolDown {
    private long start;
    private long lasts;

    public CoolDown(long lasts){
        this.lasts = lasts;
    }

    public void start(){
        this.start = System.currentTimeMillis();
    }

    public boolean hasFinished(){
        return System.currentTimeMillis() >= start + lasts;
    }

    public void finish() {
        start = 0;
    }

    public void setCooldown(long time){
        this.lasts = time;
    }

    public long getElapsedTime(){
        return System.currentTimeMillis() - this.start;
    }

    public long getTimeLeft(){
        return lasts - (System.currentTimeMillis() - start);
    }
}