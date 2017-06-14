package hudson.plugins.tfs.rm;

import com.google.gson.Gson;
import hudson.Launcher;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.*;

import java.io.*;
// import java.lang.Runtime;

/**
 * @author Ankit Goyal
 */
public class ReleaseManagementCI extends Notifier{

    public final String collectionUrl;
    public final String projectName;
    public final String releaseDefinitionName;
    public final String username;
    public final Secret password;
    public final boolean propagatedArtifacts;
    public final String artifactLocation;
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public ReleaseManagementCI(String collectionUrl, String projectName, String releaseDefinitionName, String username, Secret password, boolean propagatedArtifacts, String artifactLocation)
    {
        if (collectionUrl.endsWith("/"))
        {
            this.collectionUrl = collectionUrl;
        }
        else
        {
            this.collectionUrl = collectionUrl + "/";
        }
        
        //this.collectionUrl = this.collectionUrl.toLowerCase().replaceFirst(".visualstudio.com", ".vsrm.visualstudio.com");
        this.projectName = projectName;
        this.releaseDefinitionName = releaseDefinitionName;
        this.username = username;
        this.password = password;
        this.propagatedArtifacts = propagatedArtifacts;
        this.artifactLocation = artifactLocation;
    }

    /*
     * (non-Javadoc)
     *
     * @see hudson.tasks.BuildStep#getRequiredMonitorService()
     */
    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

        /*
     * (non-Javadoc)
     *
     * @see
     * hudson.tasks.BuildStepCompatibilityLayer#perform(hudson.model.AbstractBuild
     * , hudson.Launcher, hudson.model.BuildListener)
     */

    // private String escapeSplChars(String address)
    // {
    //     for(int i=0; i<address.length(); i++) {
    //         if(address.charAt(i) == '\\') {
    //             String temp = address.substring(0,i) + address.charAt(i) + address.substring(i,address.length());
    //             address = temp;
    //         }
    //     }
    // }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException
    {

        if(this.propagatedArtifacts)
        {
            // PAT: stgm6llpxvza3qqatraq6naoymcd6wxssvkesn5v3h4c5wq4owzq

            Process process;

            String jobName = build.getProject().getName();
            int buildId = build.number;
            // String versionNumber = "1.0.10";
            String versionNumber = "1.0."+Integer.toString(buildId);
            // String nuspecFilename = System.getProperty("jenkins.jobName") + "-artifacts.nuspec";
            String nuspecFilename = jobName + "-artifacts.nuspec";
            String nuspecContents = "<?xml version=\"1.0\"?>\r\n"+
            "<package >\n"+
                "\t<metadata>\n"+
                    "\t\t<id>"+jobName+"-artifacts</id>\n"+
                    "\t\t<version>"+versionNumber+"</version>\n"+
                    "\t\t<authors>t-vasaxe</authors>\n"+
                    "\t\t<owners>t-vasaxe</owners>\n"+
                    "\t\t<licenseUrl>http://LICENSE_URL_HERE_OR_DELETE_THIS_LINE</licenseUrl>\n"+
                    "\t\t<projectUrl>http://PROJECT_URL_HERE_OR_DELETE_THIS_LINE</projectUrl>\n"+
                    "\t\t<iconUrl>http://ICON_URL_HERE_OR_DELETE_THIS_LINE</iconUrl>\n"+
                    "\t\t<requireLicenseAcceptance>false</requireLicenseAcceptance>\n"+
                    "\t\t<description>Package description</description>\n"+
                    "\t\t<releaseNotes>Summary of changes made in this release of the package.</releaseNotes>\n"+
                    "\t\t<copyright>Copyright 2017</copyright>\n"+
                    "\t\t<tags>Tag1 Tag2</tags>\n"+
                    "\t\t<dependencies>\n"+
                        "\t\t\t<dependency id=\"SampleDependency\" version=\"1.0\" />\n"+
                    "\t\t</dependencies>\n"+
                "\t</metadata>\n"+
                "\t<files>\n"+
                    "\t\t<file src=\".\\**\" target=\"\"></file>\n"+
                "\t</files>\n"+
            "</package>";

            FileOutputStream os_nuspecCreate = new FileOutputStream(this.artifactLocation+"\\"+nuspecFilename);
            PrintStream ps_nuspecCreate = new PrintStream(os_nuspecCreate);
            ps_nuspecCreate.print(nuspecContents);
            ps_nuspecCreate.close();

            String cmd_nupkgCreate = "cmd /c cd "+this.artifactLocation+" & nuget.exe pack "+nuspecFilename;
            process = Runtime.getRuntime().exec(cmd_nupkgCreate);
            process.waitFor();
            
            String nupkgLocation = this.artifactLocation+"\\"+jobName+"-artifacts."+versionNumber+".nupkg";

            String feedName = jobName + "-artifacts-feed";
            // feedName = "Artifacts-feed";
            // String cmd_pushPkgMgmt = "cmd /c nuget.exe sources Add -Name \""+feedName+"\" -Source \"https://"+this.username+".pkgs.visualstudio.com/_packaging/"+feedName+"/nuget/v3/index.json\" -username "+this.username+" -password "+this.password+" & nuget.exe push -Source \""+feedName+"\" -ApiKey VSTS "+nupkgLocation;
            String cmd_pushPkgMgmt = "cmd /c nuget.exe sources Add -Name \""+feedName+"\" -Source \"http://localhost:8080/tfs/DefaultCollection/_packaging/"+feedName+"/nuget/v3/index.json\" & nuget.exe push -Source \""+feedName+"\" -ApiKey VSTS "+nupkgLocation;
            // cmd_pushPkgMgmt = "cmd /c nuget.exe sources Add -Name \"TaskList-artifacts-feed\" -Source \"http://localhost:8080/tfs/DefaultCollection/_packaging/TaskList-artifacts-feed/nuget/v3/index.json\" & nuget.exe push -Source \"TaskList-artifacts-feed\" -ApiKey VSTS C:\\artifacts\\TaskList-artifacts."+versionNumber+".nupkg";

            cmd_pushPkgMgmt = "cmd /c nuget.exe push -Source \""+feedName+"\" -ApiKey VSTS "+nupkgLocation;
            listener.getLogger().printf(cmd_pushPkgMgmt);
            process = Runtime.getRuntime().exec(cmd_pushPkgMgmt);

            final int exitValue = process.waitFor();
            if (exitValue == 0)
                listener.getLogger().printf("Successfully executed the command: " + cmd_pushPkgMgmt);
            else {
                listener.getLogger().printf("Failed to execute the following command: " + cmd_pushPkgMgmt + " due to the following error(s):");
                try (final BufferedReader b = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    if ((line = b.readLine()) != null)
                        listener.getLogger().printf(line);
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // FeedHttpClient feedHttpClient = 
        //             new FeedHttpClient(
        //                     this.collectionUrl.toLowerCase().replaceFirst(".visualstudio.com", ".pkgs.visualstudio.com"),
        //                     this.username,
        //                     this.password);

        // feedHttpClient.   

        String jobName = build.getProject().getName();
        int buildId = build.number;
        String buildNumber = build.getDisplayName();
        if (build.getResult() == Result.SUCCESS)
        {
            ReleaseManagementHttpClient releaseManagementHttpClient = 
                    new ReleaseManagementHttpClient(
                            this.collectionUrl.toLowerCase().replaceFirst(".visualstudio.com", ".vsrm.visualstudio.com"),
                            this.username,
                            this.password);
            
            try 
            {
                ReleaseDefinition releaseDefinition = null;
                List<ReleaseDefinition> releaseDefinitions = releaseManagementHttpClient.GetReleaseDefinitions(this.projectName);
                for(final ReleaseDefinition rd : releaseDefinitions)
                {
                    if(rd.getName().equalsIgnoreCase(this.releaseDefinitionName))
                    {
                        releaseDefinition = rd;
                        break;
                    }
                }

                if(releaseDefinition == null)
                {
                    listener.getLogger().printf("No release definition found with name: %s%n", this.releaseDefinitionName);
                    listener.getLogger().println("Release will not be triggered.");
                }
                else
                {
                    CreateRelease(releaseManagementHttpClient, releaseDefinition, jobName, buildNumber, buildId, listener);
                }
            }
            catch (ReleaseManagementException ex)
            {
                ex.printStackTrace(listener.error("Failed to trigger release.%n"));
                // ex.printStackTrace(listener.error(" "+this.propagatedArtifacts));
            }
            catch (JSONException ex)
            {
                ex.printStackTrace(listener.error("Failed to trigger release.%n"));
            }
        }

        
        
        
        return true;
    }
    
    void CreateRelease(
            ReleaseManagementHttpClient releaseManagementHttpClient,
            ReleaseDefinition releaseDefinition,
            String jobName,
            String buildNumber,
            int buildId,
            BuildListener listener) throws ReleaseManagementException, JSONException
    {
        Artifact jenkinsArtifact = null;
        for(final Artifact artifact : releaseDefinition.getArtifacts())
        {
            if(artifact.getType().equalsIgnoreCase("jenkins") && artifact.getDefinitionReference().getDefinition().getName().equalsIgnoreCase(jobName))
            {
                jenkinsArtifact = artifact;
                break;
            }
        }
        
        if(jenkinsArtifact == null)
        {
            listener.getLogger().printf("No jenkins artifact found with name: %s%n", jobName);
        }
        else
        {
            List<ReleaseArtifact> releaseArtifacts = PrepareReleaseArtifacts(
                    releaseDefinition,
                    jenkinsArtifact,
                    buildNumber,
                    buildId,
                    listener,
                    releaseManagementHttpClient);            
            String description = "Triggered by " + buildNumber;
            ReleaseBody releaseBody = new ReleaseBody();
            releaseBody.setDescription(description);
            releaseBody.setDefinitionId(releaseDefinition.getId());
            releaseBody.setArtifacts(releaseArtifacts);
            releaseBody.setIsDraft(false);
            String body  = new Gson().toJson(releaseBody);

            listener.getLogger().printf("Triggering release...%n");
            String response = releaseManagementHttpClient.CreateRelease(this.projectName, body);
            listener.getLogger().printf("Successfully triggered release.%n");
            JSONObject object = new JSONObject(response);
            listener.getLogger().printf("Release Name: %s%n", object.getString("name"));
            listener.getLogger().printf("Release id: %s%n", object.getString("id"));
        }
    }

    private List<ReleaseArtifact> PrepareReleaseArtifacts(ReleaseDefinition releaseDefinition, Artifact jenkinsArtifact, String buildNumber, int buildId, BuildListener listener, ReleaseManagementHttpClient releaseManagementHttpClient) throws ReleaseManagementException {
        List<ReleaseArtifact> releaseArtifacts = new ArrayList<ReleaseArtifact>();
        InstanceReference instanceReference = new InstanceReference();
        for(final Artifact artifact : releaseDefinition.getArtifacts())
        {
            ReleaseArtifact releaseArtifact = new ReleaseArtifact();
            if(artifact == jenkinsArtifact)
            {
                instanceReference.setName(buildNumber);
                instanceReference.setId(Integer.toString(buildId));
            }
            else
            {
                listener.getLogger().printf("Fetching latest version for artifact: %s%n", artifact.getAlias());
                ReleaseArtifactVersionsResponse response = releaseManagementHttpClient.GetVersions(this.projectName, new ArrayList<Artifact>(Arrays.asList(artifact)));
                if(response.getArtifactVersions().isEmpty())
                {
                    throw new ReleaseManagementException("Could not fetch versions for the linked artifact sources");
                }
                if(response.getArtifactVersions().get(0).getVersions().isEmpty())
                {
                    throw new ReleaseManagementException("Could not fetch versions for the linked artifact: " + artifact.getAlias());
                }
                
                instanceReference.setName(response.getArtifactVersions().get(0).getVersions().get(0).getName());
                instanceReference.setId(response.getArtifactVersions().get(0).getVersions().get(0).getId());
            }
            
            releaseArtifact.setAlias(artifact.getAlias());
            releaseArtifact.setInstanceReference(instanceReference);
            releaseArtifacts.add(releaseArtifact);
        }
        return releaseArtifacts;
    }
    
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher>
    {

        /*
         * (non-Javadoc)
         *
         * @see hudson.tasks.BuildStepDescriptor#isApplicable(java.lang.Class)
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) 
        {
            return true;
        }

        /*
         * (non-Javadoc)
         *
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName() 
        {
            return "Trigger release in TFS/Team Services";
        }

    }
}