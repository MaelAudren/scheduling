package org.objectweb.proactive.ic2d.jmxmonitoring.data;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import javax.management.MBeanServerInvocationHandler;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.objectweb.proactive.core.UniqueID;
import org.objectweb.proactive.core.body.migration.MigrationException;
import org.objectweb.proactive.core.jmx.mbean.BodyWrapperMBean;
import org.objectweb.proactive.ic2d.console.Console;
import org.objectweb.proactive.ic2d.jmxmonitoring.Activator;
import org.objectweb.proactive.ic2d.jmxmonitoring.data.listener.ActiveObjectListener;


/**
 * Class for the active object representation in the IC2D model.
 */
public class ActiveObject extends AbstractData {
    //TODO
    /** All the method names used to notify the observers */
    public enum methodName {SET_STATE,
        ADD_COMMUNICATION,
        RESET_COMMUNICATION,
        SET_REQUEST_QUEUE_LENGTH;
    }
    ;

    /** The parent object. */
    private NodeObject parent;

    /** ID used to identify the active object globally, even in case of migration. */
    private UniqueID id;

    /** The object's name (ex: ao#2) */
    private String name;

    /** Name of the class used to created the active object. */
    private String className;

    /** JMX Notification listener */
    private NotificationListener listener;

    /** State of the object (ex: WAITING_BY_NECESSITY) */
    private State currentState = State.UNKNOWN;

    /** request queue length */
    private int requestQueueLength = -1; // -1 = not known

    /** Forwards methods in an MBean's management interface through the MBean server to the BodyWrapperMBean. */
    private BodyWrapperMBean proxyMBean;

    // -------------------------------------------
    // --- Constructor ---------------------------
    // -------------------------------------------

    /**
     * Creates a new AOObject.
     * @param parent The NodeObject containing the active object
     * @param id The active object's id
     * @param className The active object's name
     * @param objectName The object name associated to this active object.
     */
    public ActiveObject(NodeObject parent, UniqueID id, String className,
        ObjectName objectName) {
        super(objectName);
        this.parent = parent;
        this.id = id;
        this.name = NamesFactory.getInstance().associateName(id, className);
        this.className = className;

        // Used to have good performances.
        getWorldObject().addActiveObject(this);

        this.listener = new ActiveObjectListener(this);

        proxyMBean = MBeanServerInvocationHandler.newProxyInstance(getConnection(),
                getObjectName(), BodyWrapperMBean.class, false);
    }

    /**
     * Returns the unique id of the active object.
     * @return An unique id.
     */
    public UniqueID getUniqueID() {
        return this.id;
    }

    /**
     * Returns the name of this active object.
     * (Example: ao#3)
     * @return The name of this active object.
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * Returns the name of the class given in parameter to the constructor.
     * @return The class name
     */
    public String getClassName() {
        return this.className;
    }

    /**
     * Change the current state
     * @param newState
     */
    public void setState(State newState) {
        if (newState.equals(currentState)) {
            return;
        }
        switch (newState) {
        case WAITING_BY_NECESSITY:
            if (currentState == State.SERVING_REQUEST) {
                currentState = State.WAITING_BY_NECESSITY_WHILE_SERVING;
            } else {
                currentState = State.WAITING_BY_NECESSITY_WHILE_ACTIVE;
            }
            break;
        case RECEIVED_FUTURE_RESULT:
            if (currentState == State.WAITING_BY_NECESSITY_WHILE_SERVING) {
                currentState = State.SERVING_REQUEST;
            } else {
                currentState = State.ACTIVE;
            }
            break;
        default:
            currentState = newState;
            break;
        }
        setChanged();
        notifyObservers(this.currentState);
    }

    /**
     * Returns the current state of this active object.
     * @return The current state.
     */
    public State getState() {
        return this.currentState;
    }

    /**
     * Returns a JMX notification listener.
     * @return a JMX notification listener.
     */
    public NotificationListener getListener() {
        return this.listener;
    }

    /**
     * Migrates this object to another node.
     * @param nodeTargetURL
     * @return true if it has successfully migrated, false otherwise.
     */
    public boolean migrateTo(String nodeTargetURL) {
        Console console = Console.getInstance(Activator.CONSOLE_NAME);
        State oldState = getState();
        setState(State.MIGRATING);
        try {
            proxyMBean.migrateTo(nodeTargetURL);
        } catch (MigrationException e) {
            console.err("Couldn't migrate " + this + " to " + nodeTargetURL);
            setState(oldState);
            console.logException(e);
            return false;
        }

        console.log("Successfully migrated " + this + " to " + nodeTargetURL);
        return true;
    }

    /**
     * Say to the model that a MigrationException occured during a migration of this active object.
     * @param migrationException
     */
    public void migrationFailed(MigrationException migrationException) {
        Console.getInstance(Activator.CONSOLE_NAME)
               .logException("The active object " + this + " didn't migrate!",
            migrationException);
    }

    @SuppressWarnings("unchecked")
    @Override
    public NodeObject getParent() {
        return this.parent;
    }

    @Override
    public String getKey() {
        return this.id.toString();
    }

    @Override
    public String getType() {
        return "active object";
    }

    @Override
    public void explore() {
        //System.out.println(this);
    }

    @Override
    public void destroy() {
        getWorldObject().removeActiveObject(getUniqueID());
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Adds a communication to this object.
     * Warning: This active object is the destination of the communication.
     * @param aoSource Source active object.
     */
    public void addCommunication(ActiveObject aoSource) {
        setChanged();
        Set<ActiveObject> comm = new HashSet<ActiveObject>();
        comm.add(aoSource);
        notifyObservers(comm);

        /*synchronized (communications) {
                communications.add(source);
        }*/
    }

    /**
     * Adds a communication to this object.
     * Warning: This active object is the destination of the communication.
     * @param sourceID The unique id of the source of the request.
     */
    public void addCommunicationFrom(UniqueID sourceID) {
        ActiveObject source = getWorldObject().findActiveObject(sourceID);
        if (source == null) {
            //TODO A faire Traiter l'erreur
            System.err.println("Can't draw a communication from id :" +
                sourceID + " to " + this);
            return;
        }
        this.addCommunication(source);
    }

    /**
     * Adds a communication to this object.
     * Warning: This active object is the source of the communication.
     * @param destinationID The unique id of the destination of the request.
     */
    public void addCommunicationTo(UniqueID destinationID) {
        ActiveObject destination = getWorldObject()
                                       .findActiveObject(destinationID);
        if (destination == null) {
            //TODO A faire Traiter l'erreur
            System.err.println("Can't draw a communication from " + this +
                " to id :" + destinationID);
            return;
        }
        destination.addCommunication(this);
    }

    @Override
    public void resetCommunications() {
        setChanged();
        notifyObservers(new HashSet<ActiveObject>());
    }

    public void addRequest() {
        this.requestQueueLength++;
        setChanged();
        notifyObservers(requestQueueLength);
    }

    public void removeRequest() {
        this.requestQueueLength--;
        setChanged();
        notifyObservers(requestQueueLength);
    }

    public void setRequestQueueLength(int requestQueueLength) {
        if (this.requestQueueLength != requestQueueLength) {
            this.requestQueueLength = requestQueueLength;
            setChanged();
            notifyObservers(requestQueueLength);
        }
    }

    public String getJobId() {
        return this.getParent().getJobId();
    }

    //
    // -- INNER CLASS -----------------------------------------------
    //
    public static class ActiveObjectComparator implements Comparator<String> {

        /**
         * Compare two active objects. (For Example: ao#3 and ao#5 give -1
         * because ao#3 has been discovered before ao#5.)
         *
         * @return -1, 0, or 1 as the first argument is less than, equal to, or
         *         greater than the second.
         */
        public int compare(String ao1, String ao2) {
            String ao1Name = ao1;
            String ao2Name = ao2;
            return -(ao1Name.compareTo(ao2Name));
        }
    }
}
