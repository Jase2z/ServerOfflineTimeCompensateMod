package com.joedobo27.sotcm;

import org.gotti.wurmunlimited.modloader.interfaces.*;
import org.gotti.wurmunlimited.modsupport.ModSupportDb;

import com.wurmonline.server.WurmCalendar;

import java.util.Properties;
import java.util.logging.Logger;
import java.time.Instant;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class ServerOfflineTimeCompensateMod implements WurmServerMod, Configurable,
        ServerStartedListener, ServerShutdownListener {

    static Logger logger = Logger.getLogger(ServerOfflineTimeCompensateMod.class.getName());
    
    /**
     * What: Epoch time<br>
     * Why: We need to know when the WU-server was last shutdown to compare with
     * start up time. Take the difference in real time and advance server time by an acceleration factor.
    */
    private static long shutdownEpoch = 0;
    public void setShutdownEpoch(long epoch) {
        shutdownEpoch = epoch;
    }
    public long getShutdownEpoch() {
        return shutdownEpoch;
    }

    /**
     * What: Multiplier<br>
     * Why: I need a configuration value to test how accelerating time will affect Wurm mechanics.
     * The default Wurm multiplier is eight times faster.
     */
    private static int accelerationMultiplier = 8;
    public void setAccelerationMultiplier(int accel) {
        accelerationMultiplier = accel;
    }
    public int getAccelerationMultiplier() {
        return accelerationMultiplier;
    }


    @Override
    public void configure(Properties properties){
        setAccelerationMultiplier(Integer.parseInt(
            properties.getProperty("accelerationMultiplier"
            , Integer.toString(getAccelerationMultiplier()))));
    }

    @Override
    public void onServerStarted() {
        advanceWurmTime();
    }

    @Override
    public void onServerShutdown() {
        setShutdownTime();
    }


    /**
     * What: Get an Epoch-like time from modsupport.db. TABLE: epoch, COLUMN: shutdown.
     * This sets the class variable {@link shutdownEpoch}<br>
     * 
     * Why: We need persistent storage for a real shutdown time for when the server isn't running.
     * An SQL database was used for conformity since all persistent information is stored
     * in a SQL db.
     */
    private void getShutdownTime() {
        // Get the last Shutdown Epoch time from mod database.
        try (Connection dbCon = ModSupportDb.getModSupportDb();
            Statement stmt = dbCon.createStatement()) {
            if (!ModSupportDb.hasTable(dbCon, "epoch")) {
                stmt.execute("CREATE TABLE epoch (shutdown INT)");
                setShutdownEpoch(Instant.now().getEpochSecond());
                stmt.execute(String.format("INSERT INTO epoch VALUES (%d)"
                , getShutdownEpoch()));
                ServerOfflineTimeCompensateMod.logger.info(
                    String.format("epoch table missing, thus created. Shutdown set to %d"
                    ,getShutdownEpoch()));
            } else {
                ResultSet rs = stmt.executeQuery("SELECT shutdown FROM epoch");
                //harper:ignore
                //TODO there should only ever be one ResultSet.
                while (rs.next()) {
                    setShutdownEpoch(rs.getLong("shutdown"));
                }
                //harper:ignore
                //ServerOfflineTimeCompensateMod.logger.info(
                //    String.format("success on fetch shutdown time %d, epoch %d"
                //                , shutdownEpoch, Instant.now().getEpochSecond()));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return;
    }

    /**
     * What: Advance {@link WurmCalendar.currentTime} as a function of offline duration
     * and an acceleration multiplier.<br>
     * 
     * Why: Wurm time does not advance when the server is offline. This is part of the
     * process to advance Wurm time based partially on server offline time.  
     */
    private void advanceWurmTime() {
        getShutdownTime();
        int accel = getAccelerationMultiplier();
        long elapsedTime = Instant.now().getEpochSecond() - getShutdownEpoch();
        if (elapsedTime < 0) {
            ServerOfflineTimeCompensateMod.logger.warning(
                String.format("negative time passage. Elapsed %d, now %d, shutdown %d. Set elapsed to 0."
            , elapsedTime, Instant.now().getEpochSecond(), getShutdownEpoch()));
            elapsedTime = 0;
        }
        WurmCalendar.currentTime = (long)(WurmCalendar.getCurrentTime() + elapsedTime * accel);
        double secPerDay = 86400.0;
        double days = (double)elapsedTime * (double)accel / secPerDay; 
        ServerOfflineTimeCompensateMod.logger.info(
            String.format("Advanced server time by %.6f Wurm days. Elapsed real seconds %d"
            , days, elapsedTime));
    }

    /**
     * What: Set an Epoch-like time into modsupport.db. TABLE: epoch, COLUMN: shutdown.<br>
     * 
     * Why: We need persistent storage of a real shutdown time. It's used when the server is started and to
     * advance Wurm time.
     * An SQL database was used for conformity since all persistent information is stored
     * in a SQL db.
     */
    private void setShutdownTime(){
        // Update shutdown Epoch time.
        try (Connection dbCon = ModSupportDb.getModSupportDb();
            Statement stmt = dbCon.createStatement();) {
            if (!ModSupportDb.hasTable(dbCon, "epoch")) {
                stmt.execute("CREATE TABLE epoch (shutdown INT)");
                setShutdownEpoch(Instant.now().getEpochSecond());
                stmt.execute(String.format("INSERT INTO epoch VALUES (%d)"
                , getShutdownEpoch()));
                ServerOfflineTimeCompensateMod.logger.info(
                    String.format("epoch table missing, thus created. Shutdown set to %d"
                    , getShutdownEpoch()));
            } else {
                setShutdownEpoch(Instant.now().getEpochSecond());
                stmt.execute(String.format("UPDATE epoch SET shutdown = %d"
                , getShutdownEpoch()));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
