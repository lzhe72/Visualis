import * as React from 'react'

import { Button, Tooltip, Popover, Col, Popconfirm } from 'antd'
import { ButtonProps } from 'antd/lib/button/button'

import { IProject } from 'containers/Projects'
import { ICurrentDashboard } from '../'

import ModulePermission from 'containers/Account/components/checkModulePermission'
import ShareDownloadPermission from 'containers/Account/components/checkShareDownloadPermission'

import SharePanel from 'components/SharePanel'

const utilStyles = require('assets/less/util.less')
const dashboardStyles = require('../Dashboard.less')

interface IDashboardToolbarProps {
  currentProject: IProject
  currentDashboard: ICurrentDashboard
  currentDashboardShareInfo: string
  currentDashboardSecretInfo: string
  currentDashboardShareInfoLoading: boolean
  dashboardSharePanelAuthorized: boolean
  showAddDashboardItem: () => void
  onChangeDashboardAuthorize: (authorized: boolean) => () => void
  onLoadDashboardShareLink: (id: number, authName: string) => void
  onToggleLinkageVisibility: (visible: boolean) => () => void
  onToggleGlobalFilterVisibility: (visible: boolean) => () => void
  onDownloadDashboard: () => void
}

export class DashboardToolbar extends React.PureComponent<IDashboardToolbarProps> {

  public render () {
    const { currentDashboard } = this.props
    if (!currentDashboard) { return null }

    const {
      currentProject,
      currentDashboardShareInfo,
      currentDashboardSecretInfo,
      currentDashboardShareInfoLoading,
      dashboardSharePanelAuthorized,
      onChangeDashboardAuthorize,
      showAddDashboardItem,
      onLoadDashboardShareLink,
      onToggleLinkageVisibility,
      onToggleGlobalFilterVisibility,
      onDownloadDashboard
    } = this.props

    const AddButton = ModulePermission<ButtonProps>(currentProject, 'viz', true)(Button)
    const ShareButton = ShareDownloadPermission<ButtonProps>(currentProject, 'share')(Button)
    const DownloadButton = ShareDownloadPermission<ButtonProps>(currentProject, 'download')(Button)
    const LinkageButton = ModulePermission<ButtonProps>(currentProject, 'viz', false)(Button)
    const GlobalFilterButton = ModulePermission<ButtonProps>(currentProject, 'viz', false)(Button)

    let addButton
    let shareButton
    let downloadButton
    let linkageButton
    let globalFilterButton
    let previewButton

    addButton = (
      <Tooltip placement="bottom" title="??????">
        <AddButton
          type="primary"
          icon="plus"
          style={{marginLeft: '8px'}}
          onClick={showAddDashboardItem}
        />
      </Tooltip>
    )
    shareButton = (
      <Popover
        placement="bottomRight"
        content={
          <SharePanel
            id={currentDashboard.id}
            type="dashboard"
            shareInfo={currentDashboardShareInfo}
            secretInfo={currentDashboardSecretInfo}
            shareInfoLoading={currentDashboardShareInfoLoading}
            authorized={dashboardSharePanelAuthorized}
            afterAuthorization={onChangeDashboardAuthorize(true)}
            onLoadDashboardShareLink={onLoadDashboardShareLink}
          />
        }
        trigger="click"
      >
        <Tooltip placement="bottom" title="??????">
          <ShareButton
            type="primary"
            icon="share-alt"
            style={{marginLeft: '8px'}}
            onClick={onChangeDashboardAuthorize(false)}
          />
        </Tooltip>
      </Popover>
    )
    downloadButton = (
      <Tooltip placement="bottom" title="??????">
        <Popconfirm
          title="??????????????????"
          placement="bottom"
          onConfirm={onDownloadDashboard}
        >
          <DownloadButton
            type="primary"
            icon="download"
            style={{marginLeft: '8px'}}
          />
        </Popconfirm>
      </Tooltip>
    )
    linkageButton = (
      <Tooltip placement="bottom" title="??????????????????">
        <LinkageButton
          type="primary"
          icon="link"
          style={{marginLeft: '8px'}}
          onClick={onToggleLinkageVisibility(true)}
        />
      </Tooltip>
    )
    globalFilterButton = (
      <Tooltip placement="bottomRight" title="?????????????????????">
        <GlobalFilterButton
          type="primary"
          icon="filter"
          style={{marginLeft: '8px'}}
          onClick={onToggleGlobalFilterVisibility(true)}
        />
      </Tooltip>
    )
    previewButton = (
      <Button className={dashboardStyles.previewButton}  type="primary">
        <a target="_blank" href={`./#/project/${currentProject.id}/dashboard/preview/${currentDashboard.id}`}>
        <i className="iconfont icon-preview" />
        </a>
      </Button>
    )

    return (
      <Col sm={12} className={utilStyles.textAlignRight}>
        {addButton}
        {shareButton}
        {/* {downloadButton} */}
        {linkageButton}
        {globalFilterButton}
        {previewButton}
      </Col>
    )
  }
}

export default DashboardToolbar
