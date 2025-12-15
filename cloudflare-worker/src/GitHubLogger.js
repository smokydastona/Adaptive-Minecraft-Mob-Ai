/**
 * GitHub Logger - Observability Layer (Side Effect Only)
 * 
 * CRITICAL: This NEVER affects federation logic.
 * All writes are wrapped in try/catch - failures are silent.
 * GitHub is a read-only flight recorder of what already happened.
 * 
 * Purpose: Debug federation rounds, track history, visualize activity
 */

export class GitHubLogger {
  constructor(token, repo) {
    this.token = token;
    this.repo = repo; // Format: "owner/repo"
    this.baseUrl = `https://api.github.com/repos/${repo}/contents`;
  }

  /**
   * Log a completed federation round
   * Called AFTER aggregation completes (side effect only)
   */
  async logRound(roundData) {
    try {
      const filename = `rounds/round-${String(roundData.round).padStart(6, '0')}.json`;
      const content = JSON.stringify({
        round: roundData.round,
        timestamp: roundData.timestamp || new Date().toISOString(),
        contributors: roundData.contributors,
        mobTypes: roundData.mobTypes || [],
        modelStats: roundData.modelStats || {},
        metadata: {
          loggedAt: new Date().toISOString(),
          source: 'federation-coordinator'
        }
      }, null, 2);

      await this.writeFile(filename, content, `Log federation round ${roundData.round}`);
      console.log(`📝 GitHub: Logged round ${roundData.round}`);
    } catch (error) {
      // SILENT FAILURE - never break federation
      console.warn(`⚠️ GitHub logging failed (non-critical): ${error.message}`);
    }
  }

  /**
   * Log an upload event (optional, for detailed tracking)
   */
  async logUpload(uploadData) {
    try {
      const date = new Date().toISOString().split('T')[0];
      const filename = `uploads/${date}.jsonl`;
      
      // Append to daily log file (JSONL format)
      const line = JSON.stringify({
        timestamp: new Date().toISOString(),
        serverId: uploadData.serverId,
        mobType: uploadData.mobType,
        round: uploadData.round,
        bootstrap: uploadData.bootstrap || false
      });

      await this.appendToFile(filename, line + '\n');
      console.log(`📝 GitHub: Logged upload from ${uploadData.serverId}`);
    } catch (error) {
      console.warn(`⚠️ GitHub upload logging failed (non-critical): ${error.message}`);
    }
  }

  /**
   * Log current status snapshot (for monitoring)
   */
  async logStatus(statusData) {
    try {
      const filename = 'status/latest.json';
      const content = JSON.stringify({
        ...statusData,
        lastUpdated: new Date().toISOString()
      }, null, 2);

      await this.writeFile(filename, content, 'Update federation status');
      console.log(`📝 GitHub: Updated status snapshot`);
    } catch (error) {
      console.warn(`⚠️ GitHub status logging failed (non-critical): ${error.message}`);
    }
  }

  /**
   * Write a file to GitHub (create or update)
   */
  async writeFile(path, content, message) {
    const url = `${this.baseUrl}/${path}`;
    
    // Check if file exists (to get SHA for update)
    let sha = null;
    try {
      const existingResponse = await fetch(url, {
        headers: {
          'Authorization': `Bearer ${this.token}`,
          'Accept': 'application/vnd.github.v3+json',
          'User-Agent': 'MCA-AI-Federation'
        }
      });
      
      if (existingResponse.ok) {
        const existing = await existingResponse.json();
        sha = existing.sha;
      }
    } catch (error) {
      // File doesn't exist yet, that's fine
    }

    // Write file
    const body = {
      message,
      content: btoa(content), // Base64 encode
      ...(sha && { sha }) // Include SHA if updating
    };

    const response = await fetch(url, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${this.token}`,
        'Accept': 'application/vnd.github.v3+json',
        'Content-Type': 'application/json',
        'User-Agent': 'MCA-AI-Federation'
      },
      body: JSON.stringify(body)
    });

    if (!response.ok) {
      const error = await response.text();
      throw new Error(`GitHub API error: ${response.status} - ${error}`);
    }

    return await response.json();
  }

  /**
   * Append to a file (for JSONL logs)
   * Note: GitHub doesn't support true append, so we read+write
   */
  async appendToFile(path, newContent) {
    const url = `${this.baseUrl}/${path}`;
    
    // Read existing content
    let existingContent = '';
    let sha = null;
    
    try {
      const response = await fetch(url, {
        headers: {
          'Authorization': `Bearer ${this.token}`,
          'Accept': 'application/vnd.github.v3+json',
          'User-Agent': 'MCA-AI-Federation'
        }
      });
      
      if (response.ok) {
        const data = await response.json();
        existingContent = atob(data.content);
        sha = data.sha;
      }
    } catch (error) {
      // File doesn't exist, start fresh
    }

    // Append new content
    const updatedContent = existingContent + newContent;

    // Write back
    const body = {
      message: `Append federation log`,
      content: btoa(updatedContent),
      ...(sha && { sha })
    };

    const response = await fetch(url, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${this.token}`,
        'Accept': 'application/vnd.github.v3+json',
        'Content-Type': 'application/json',
        'User-Agent': 'MCA-AI-Federation'
      },
      body: JSON.stringify(body)
    });

    if (!response.ok) {
      throw new Error(`GitHub append failed: ${response.status}`);
    }

    return await response.json();
  }
}
