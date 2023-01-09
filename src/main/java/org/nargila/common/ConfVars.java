package org.nargila.common;

import io.github.cdimascio.dotenv.Dotenv;


/**
 * Dotenv wrapper class adding some constraints and type casting.
 */
public class ConfVars {

    /**
     * dotenv instance.
     */
    private final Dotenv dotenv;

    /**
     * constructior.
     */
    public ConfVars() {
        this.dotenv = Dotenv.load();
    }

    /**
     * Get config var.
     * @param envName  env variable name
     * @return env var value if found, else exception is thrown
     */
    public String get(String envName) {
        final String val = this.dotenv.get(envName);
        if (val == null) {
            throw new IndexOutOfBoundsException(String.format(
                    "missing env variable '%s'",
                    envName
            ));
        }
        return val;
    }

    /**
     * Get config var as int.
     * @param envName  env variable name
     * @return env var value as int if found, else exception is thrown
     */
    public int getInt(String envName) {
        return Integer.parseInt(this.get(envName));
    }

    /**
     * Get config var as int with default value.
     * @param envName  env variable name
     * @param defValue default value
     * @return env var value as int if found, else provided defValue
     */
    public int get(String envName, int defValue) {
        return Integer.parseInt(this.dotenv.get(envName, defValue + ""));
    }

    /**
     * Get config var as boolean with default value.
     * @param envName  env variable name
     * @param defValue default value
     * @return env var value as boolean if found, else provided defValue
     */
    public boolean get(String envName, boolean defValue) {

        return Boolean.parseBoolean(this.dotenv.get(envName, defValue + ""));
    }

    /**
     * Get config var with default.
     * envName can be missing in environment
     * @param envName env variable name
     * @param defValue default value
     * @return env var value if found, else provided defValue
     */
    public String get(String envName, String defValue) {
        return this.dotenv.get(envName, defValue);
    }
}
